package com.hbs.site.module.bfm.engine.expression;

import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 表达式求值器 - 终极修复版 v2
 * 修复点：
 * 1. 正确处理 #{...} 和 ${...} 包裹
 * 2. 正确处理 {#var} 集合字面量（返回 Set）
 * 3. 正确处理赋值表达式 #bean.prop = value（含集合字面量）
 * 4. 正确处理复杂 SpEL（T(System).xxx()、字符串拼接等）
 * 5. 复杂 SpEL 中正确注册流程变量（关键修复：'prefix_' + #var）
 */
@Slf4j
@Component
public class ExpressionEvaluator {

    private final ExpressionParser expressionParser = new SpelExpressionParser();

    // 类全限定名
    private static final Pattern QUALIFIED_CLASS_PATTERN = Pattern.compile(
            "^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*\\.[A-Z][a-zA-Z0-9_]*$"
    );

    // 简单变量路径：#var 或 #var.prop
    private static final Pattern SIMPLE_VAR_PATH_PATTERN = Pattern.compile(
            "^#[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$"
    );

    public Object evaluate(String expression, ExecutionContext context, ActivityInstance activityInstance) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }

        String originalExpr = expression.trim();
        boolean wasWrapped = false;
        String expr = originalExpr;

        // 解包 #{...} 或 ${...}
        if ((originalExpr.startsWith("#{") || originalExpr.startsWith("${")) && originalExpr.endsWith("}")) {
            expr = originalExpr.substring(2, originalExpr.length() - 1).trim();
            wasWrapped = true;
            log.debug("解包表达式: {} -> {}", originalExpr, expr);
        }

        try {
            // 1. 赋值表达式（必须在集合字面量之前检查，因为 #a = {1,2,3} 包含 = 和 {}）
            if (!wasWrapped && isAssignmentExpression(expr)) {
                return handleAssignmentExpression(expr, context, activityInstance);
            }

            // 2. 处理 #result
            if (expr.startsWith("#result")) {
                return handleResultExpression(expr, activityInstance);
            }

            // 3. 处理 Set/List 字面量：{#var} 或 {1,2,3}（注意：不是 #{...} 解包后的）
            if (expr.startsWith("{") && expr.endsWith("}") && !wasWrapped) {
                return handleSetLiteralExpression(expr, context, activityInstance);
            }

            // 4. 处理类全限定名
            if (!expr.startsWith("#") && isQualifiedClassName(expr)) {
                return handleClassInstantiation(expr);
            }

            // 5. 复杂 SpEL（T(...)、方法调用、运算符等）
            if (isObviouslyComplexSpel(expr)) {
                return evaluateComplexExpressionSafe(expr, context, activityInstance);
            }

            // 6. 简单变量路径
            if (isSimpleVariablePath(expr)) {
                return handleVariableExpression(expr, context, activityInstance);
            }

            // 7. 兜底：作为复杂 SpEL
            return evaluateComplexExpressionSafe(expr, context, activityInstance);

        } catch (Exception e) {
            log.error("表达式求值失败: {} (解包后: {})", originalExpr, expr, e);
            throw new RuntimeException(
                    String.format("表达式求值失败: %s, 错误: %s", originalExpr, e.getMessage()), e
            );
        }
    }

    /**
     * 判断是否为赋值表达式（#bean.prop = value）
     * 排除 ==、!=、<=、>=、:=、=>
     */
    private boolean isAssignmentExpression(String expr) {
        if (!expr.contains("=")) return false;
        if (expr.contains("==") || expr.contains("!=") ||
                expr.contains("<=") || expr.contains(">=") ||
                expr.contains(":=") || expr.contains("=>")) {
            return false;
        }
        if (!expr.trim().startsWith("#")) return false;

        int eqIndex = expr.indexOf('=');
        if (eqIndex <= 0 || eqIndex >= expr.length() - 1) return false;

        String left = expr.substring(0, eqIndex).trim();
        return left.startsWith("#") && left.contains(".");
    }

    /**
     * 处理集合字面量：{#postId} 或 {#a, #b}
     */
    private Object handleSetLiteralExpression(String expr, ExecutionContext context, ActivityInstance activityInstance) {
        String inner = expr.substring(1, expr.length() - 1).trim();
        if (inner.isEmpty()) {
            return new LinkedHashSet<>();
        }

        Set<Object> result = new LinkedHashSet<>();
        String[] elements = splitRespectingBrackets(inner);

        for (String element : elements) {
            String trimmed = element.trim();
            if (trimmed.isEmpty()) continue;
            Object value = evaluate(trimmed, context, activityInstance);
            if (value != null) {
                result.add(value);
            }
        }
        log.debug("集合字面量求值: {} -> Set(size={})", expr, result.size());
        return result;
    }

    /**
     * 简单分割，考虑括号层级
     */
    private String[] splitRespectingBrackets(String str) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketDepth = 0;

        for (char c : str.toCharArray()) {
            if (c == '{') bracketDepth++;
            else if (c == '}') bracketDepth--;

            if (c == ',' && bracketDepth == 0) {
                parts.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString().trim());
        }
        return parts.toArray(new String[0]);
    }

    /**
     * 处理赋值表达式：#userRequest.postIds = {#postId}
     */
    private Object handleAssignmentExpression(String expr, ExecutionContext context, ActivityInstance activityInstance) {
        try {
            int eqIndex = expr.indexOf('=');
            String targetExpr = expr.substring(0, eqIndex).trim();
            String sourceExpr = expr.substring(eqIndex + 1).trim();

            // 求值右侧（支持集合字面量 {#postId}）
            Object sourceValue = evaluate(sourceExpr, context, activityInstance);

            // 解析左侧 #beanName.propertyPath
            if (!targetExpr.startsWith("#") || !targetExpr.contains(".")) {
                throw new IllegalArgumentException("赋值目标必须是 #bean.prop 格式: " + targetExpr);
            }

            String targetPath = targetExpr.substring(1);
            String[] parts = targetPath.split("\\.", 2);
            String beanName = parts[0];
            String propPath = parts[1];

            Object targetBean = context.getProcessInstance().getVariable(beanName);
            if (targetBean == null) {
                throw new IllegalArgumentException("赋值目标 Bean 不存在: " + beanName);
            }

            setPropertyByPath(targetBean, propPath, sourceValue);
            log.debug("赋值成功: {}.{} = {}", beanName, propPath, sourceValue);
            return targetBean;

        } catch (Exception e) {
            log.error("属性赋值失败: {}", expr, e);
            throw new RuntimeException("属性赋值失败: " + expr, e);
        }
    }

    /**
     * 递归设置对象属性
     */
    private void setPropertyByPath(Object target, String propPath, Object value) throws Exception {
        String[] props = propPath.split("\\.");
        Object current = target;

        for (int i = 0; i < props.length - 1; i++) {
            String prop = props[i];
            Field field = findField(current.getClass(), prop);
            if (field == null) {
                throw new NoSuchFieldException("字段不存在: " + prop);
            }
            field.setAccessible(true);
            Object next = field.get(current);

            if (next == null) {
                Class<?> fieldType = field.getType();
                if (Map.class.isAssignableFrom(fieldType)) {
                    next = new HashMap<>();
                    field.set(current, next);
                } else {
                    try {
                        next = fieldType.newInstance();
                        field.set(current, next);
                    } catch (Exception e) {
                        throw new RuntimeException("无法实例化中间对象: " + fieldType, e);
                    }
                }
            }
            current = next;
        }

        String finalProp = props[props.length - 1];
        Field field = findField(current.getClass(), finalProp);
        if (field == null) {
            throw new NoSuchFieldException("字段不存在: " + finalProp);
        }
        field.setAccessible(true);
        Object convertedValue = convertType(value, field.getType());
        field.set(current, convertedValue);
    }

    /**
     * 类型转换增强版：支持 List->Set
     */
    private Object convertType(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isAssignableFrom(value.getClass())) return value;

        // List/Collection -> Set
        if ((targetType == Set.class || targetType == HashSet.class || targetType == LinkedHashSet.class)
                && value instanceof Collection) {
            return targetType == LinkedHashSet.class ?
                    new LinkedHashSet<>((Collection<?>) value) : new HashSet<>((Collection<?>) value);
        }

        // Collection -> List
        if ((targetType == List.class || targetType == ArrayList.class) && value instanceof Collection) {
            return new ArrayList<>((Collection<?>) value);
        }

        // 基本类型转换
        if (targetType == Long.class || targetType == long.class) {
            return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
        } else if (targetType == Integer.class || targetType == int.class) {
            return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
        } else if (targetType == String.class) {
            return value.toString();
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(value.toString());
        } else if (targetType == Double.class || targetType == double.class) {
            return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
        }

        return value;
    }

    /**
     * 判断是否复杂 SpEL
     */
    private boolean isObviouslyComplexSpel(String expr) {
        if (expr == null || expr.isEmpty()) return false;
        String check = expr.startsWith("#") ? expr.substring(1) : expr;

        return check.contains("(") ||
                check.startsWith("T(") ||
                check.matches(".*[\\+\\*\\/\\%\\^].*") ||
                (check.contains("?") && check.contains(":")) ||
                check.contains(" matches ") ||
                check.contains(".class") ||
                check.matches(".*\\bnew\\s+[A-Z].*");
    }

    private boolean isQualifiedClassName(String expr) {
        return expr != null && !expr.isEmpty() &&
                !expr.contains(" ") && !expr.contains("(") && !expr.contains("#") &&
                QUALIFIED_CLASS_PATTERN.matcher(expr).matches();
    }

    private boolean isSimpleVariablePath(String expr) {
        return expr != null && SIMPLE_VAR_PATH_PATTERN.matcher(expr).matches();
    }

    private Object handleClassInstantiation(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Object instance = instantiateVO(clazz);
            log.debug("类实例化: {} -> {}", className, instance.getClass().getSimpleName());
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("类实例化失败: " + className, e);
        }
    }

    private Object handleVariableExpression(String expr, ExecutionContext context, ActivityInstance activityInstance) {
        String varPath = expr.substring(1);

        if (varPath.startsWith("workflow.")) {
            return context.getProcessInstance().getVariable(varPath.substring(9));
        }
        if (varPath.startsWith("local.")) {
            return activityInstance != null ? activityInstance.getLocalVariables().get(varPath.substring(6)) : null;
        }
        if (varPath.startsWith("package.")) {
            return context.getProcessInstance().getRuntimeWorkflow().getRuntimePackage()
                    .getPackageVariable(varPath.substring(8));
        }

        if (varPath.contains(".")) {
            return getNestedVariableValue(varPath, context, activityInstance);
        }

        return getVariableFromScope(varPath, context, activityInstance);
    }

    private Object getNestedVariableValue(String varPath, ExecutionContext context, ActivityInstance activityInstance) {
        String[] parts = varPath.split("\\.", 2);
        Object root = getVariableFromScope(parts[0], context, activityInstance);
        if (root == null || parts.length == 1) return root;

        String[] props = parts[1].split("\\.");
        Object current = root;

        for (String prop : props) {
            if (current == null) return null;
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(prop);
            } else {
                try {
                    Field f = findField(current.getClass(), prop);
                    if (f == null) return null;
                    f.setAccessible(true);
                    current = f.get(current);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return current;
    }

    private Object getVariableFromScope(String varName, ExecutionContext context, ActivityInstance activityInstance) {
        // 1. 先查流程变量（主要数据存储）
        Object processVar = context.getProcessInstance().getVariable(varName);
        if (processVar != null) {
            return processVar;
        }

        // 2. 再查 local（仅作为临时覆盖）
        if (activityInstance != null) {
            Object localVar = activityInstance.getLocalVariables().get(varName);
            if (localVar != null) {
                return localVar;
            }
        }

        // 3. 最后查 package
        return context.getProcessInstance().getRuntimeWorkflow().getRuntimePackage()
                .getPackageVariable(varName);
    }

    private Object handleResultExpression(String expr, ActivityInstance activityInstance) {
        if (activityInstance == null) return null;
        if ("#result".equals(expr)) {
            return activityInstance.getOutputData().get("result");
        }
        if (expr.startsWith("#result.")) {
            return activityInstance.getOutputData().get(expr.substring(8));
        }
        return null;
    }

    /**
     * 关键修复：复杂 SpEL 求值时，必须注册所有流程变量
     * 以支持 'prefix_' + #varName 这样的表达式
     */
    private Object evaluateComplexExpressionSafe(String expr, ExecutionContext context, ActivityInstance activityInstance) {
        StandardEvaluationContext evalContext = new StandardEvaluationContext();
        evalContext.setRootObject(context.getProcessInstance());

        evalContext.setVariable("process", context.getProcessInstance());
        evalContext.setVariable("workflow", context.getProcessInstance().getRuntimeWorkflow());
        evalContext.setVariable("variables", context.getProcessInstance().getVariables());
        evalContext.setVariable("context", context);

        // 关键修复：注册所有流程变量，支持在复杂SpEL中引用（如：'POSTCode_' + #postName）
        context.getProcessInstance().getVariables().forEach(evalContext::setVariable);

        if (activityInstance != null) {
            evalContext.setVariable("activity", activityInstance);
            evalContext.setVariable("local", activityInstance.getLocalVariables());
            evalContext.setVariable("output", activityInstance.getOutputData());
        }

        evalContext.addPropertyAccessor(new MapPropertyAccessor());
        evalContext.addPropertyAccessor(new NullSafeBeanPropertyAccessor());

        try {
            Expression spel = expressionParser.parseExpression(expr);
            return spel.getValue(evalContext);
        } catch (Exception e) {
            throw new RuntimeException("SpEL求值失败: " + expr + ", 原因: " + e.getMessage(), e);
        }
    }

    public boolean evaluateCondition(String condition, ExecutionContext context, ActivityInstance activityInstance) {
        if (condition == null || condition.trim().isEmpty()) return true;
        Object result = evaluate(condition, context, activityInstance);
        if (result instanceof Boolean) return (Boolean) result;
        if (result instanceof String) return Boolean.parseBoolean((String) result);
        if (result instanceof Number) return ((Number) result).intValue() != 0;
        return result != null;
    }

    private Field findField(Class<?> clazz, String fieldName) {
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                // continue
            }
        }
        return null;
    }

    public Object instantiateVO(Class<?> clazz) throws Exception {
        Object instance = clazz.newInstance();
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.get(instance) == null) {
                    setFieldDefault(instance, field);
                }
            }
        }
        return instance;
    }

    private void setFieldDefault(Object instance, Field field) throws IllegalAccessException {
        Class<?> type = field.getType();
        if (type == String.class) field.set(instance, "");
        else if (type == Integer.class || type == int.class) field.set(instance, 0);
        else if (type == Long.class || type == long.class) field.set(instance, 0L);
        else if (type == Boolean.class || type == boolean.class) field.set(instance, false);
        else if (type == Double.class || type == double.class) field.set(instance, 0.0);
        else if (type == Float.class || type == float.class) field.set(instance, 0.0f);
        else if (Set.class.isAssignableFrom(type)) field.set(instance, new HashSet<>());
        else if (List.class.isAssignableFrom(type)) field.set(instance, new ArrayList<>());
        else if (Map.class.isAssignableFrom(type)) field.set(instance, new HashMap<>());
    }

    // 属性访问器
    private static class MapPropertyAccessor implements PropertyAccessor {
        @Override
        public Class<?>[] getSpecificTargetClasses() {
            return new Class<?>[]{Map.class};
        }

        @Override
        public boolean canRead(EvaluationContext ctx, Object target, String name) {
            return target instanceof Map;
        }

        @SuppressWarnings("unchecked")
        @Override
        public TypedValue read(EvaluationContext ctx, Object target, String name) {
            return new TypedValue(target != null ? ((Map<String, Object>) target).get(name) : null);
        }

        @Override
        public boolean canWrite(EvaluationContext ctx, Object target, String name) {
            return target instanceof Map;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void write(EvaluationContext ctx, Object target, String name, Object value) {
            if (target != null) ((Map<String, Object>) target).put(name, value);
        }
    }

    private static class NullSafeBeanPropertyAccessor implements PropertyAccessor {
        @Override
        public Class<?>[] getSpecificTargetClasses() {
            return null;
        }

        @Override
        public boolean canRead(EvaluationContext ctx, Object target, String name) {
            return target != null && !(target instanceof Map);
        }

        @Override
        public TypedValue read(EvaluationContext ctx, Object target, String name) {
            if (target == null) return new TypedValue(null);
            try {
                Field f = findField(target.getClass(), name);
                if (f != null) {
                    f.setAccessible(true);
                    return new TypedValue(f.get(target));
                }
            } catch (Exception e) {
            }
            return new TypedValue(null);
        }

        @Override
        public boolean canWrite(EvaluationContext ctx, Object target, String name) {
            return target != null && !(target instanceof Map);
        }

        @Override
        public void write(EvaluationContext ctx, Object target, String name, Object value) {
            if (target == null) return;
            try {
                Field f = findField(target.getClass(), name);
                if (f != null) {
                    f.setAccessible(true);
                    f.set(target, value);
                }
            } catch (Exception e) {
            }
        }

        private static Field findField(Class<?> clazz, String name) {
            for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    return c.getDeclaredField(name);
                } catch (NoSuchFieldException e) {
                }
            }
            return null;
        }
    }
}