package com.hbs.site.module.bfm.engine.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbs.site.module.bfm.data.define.DataMapping;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ExecutionContext;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.expression.ExpressionEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据映射输入预处理器 - TX模式修复版
 * ✅ 修复：非必填参数为null时，仍保留Key（值为null），确保TX模式参数匹配
 * ✅ 保持：SYNC模式下的对象重建和集合转换逻辑
 */
@Slf4j
@Component
public class DataMappingInputProcessor {

    private final ExpressionEvaluator expressionEvaluator;
    private final ObjectMapper objectMapper;

    public DataMappingInputProcessor(ExpressionEvaluator expressionEvaluator, ObjectMapper objectMapper) {
        this.expressionEvaluator = expressionEvaluator;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> processInputs(DataMapping dataMapping,
                                             ActivityInstance activityInst,
                                             ServiceOrchestrationEngine engine) {
        if (dataMapping == null || dataMapping.getInputs() == null) {
            log.debug("无输入数据映射配置");
            return new LinkedHashMap<>();
        }

        log.info("开始处理活动 {} 的输入数据映射，映射项数量: {}",
                activityInst.getActivityId(), dataMapping.getInputs().size());

        Map<String, ObjectWrapper> objectWrappers = new LinkedHashMap<>();
        Map<String, Object> pureFlatVariables = new LinkedHashMap<>();
        Map<String, String> beanClassMap = new HashMap<>();

        Map<String, Object> existingBeans = collectExistingBeans(dataMapping, activityInst);
        List<String> requiredParams = new ArrayList<>();

        for (DataMapping.InputMapping input : dataMapping.getInputs()) {
            try {
                log.debug("处理映射项: source='{}', target='{}', required={}, dataType={}",
                        input.getSource(), input.getTarget(), input.getRequired(), input.getDataType());

                // 1. 求值source表达式
                Object sourceValue = expressionEvaluator.evaluate(
                        input.getSource(), activityInst.getProcessInst().getCurrentContext(), activityInst);

                // ✅ 修复点1：必填校验提前，但未必填且为null时仍保留Key
                boolean isRequired = Boolean.TRUE.equals(input.getRequired());
                if (isRequired) {
                    requiredParams.add(input.getTarget());
                    if (sourceValue == null) {
                        String errorMsg = String.format("❌ 必填参数求值为null: source='%s', target='%s'",
                                input.getSource(), input.getTarget());
                        log.error(errorMsg);
                        throw new IllegalArgumentException(errorMsg);
                    }
                }

                // 2. 处理target（关键修复：即使sourceValue为null，非必填也要保留Key）
                String target = input.getTarget();
                if (target == null) continue;

                // 先进行类型转换（如果是null，convertByDataType会原样返回null，但这里我们统一处理）
                if (sourceValue != null) {
                    sourceValue = convertByDataType(sourceValue, input);
                }

                // ✅ 核心修复：无论是否为null，都存入Variables（TX模式需要知道有多少参数）
                if (target.contains(".")) {
                    processNestedProperty(sourceValue, target, objectWrappers,
                            pureFlatVariables, existingBeans, activityInst);
                } else {
                    String cleanTarget = cleanTarget(target);
                    pureFlatVariables.put(cleanTarget, sourceValue); // 允许null值
                    log.debug("存储输入变量: {} = {} (isNull={})",
                            cleanTarget, sourceValue, sourceValue == null);
                }

            } catch (Exception e) {
                log.error("输入映射失败: source={}, target={}", input.getSource(), input.getTarget(), e);
                throw new RuntimeException("输入映射失败: " + input.getTarget(), e);
            }
        }

        // 最终校验必填项（确保非空）
        validateRequiredParameters(pureFlatVariables, objectWrappers, requiredParams, activityInst.getActivityId());

        // 合并结果
        Map<String, Object> finalResult = new LinkedHashMap<>();
        mergeObjectWrappers(finalResult, objectWrappers, pureFlatVariables, beanClassMap);

        log.info("✅ 输入数据映射完成，活动ID: {}, 参数数量: {}",
                activityInst.getActivityId(), finalResult.size());
        return finalResult;
    }

    private void validateRequiredParameters(Map<String, Object> flatVars,
                                            Map<String, ObjectWrapper> wrappers,
                                            List<String> requiredParams,
                                            String activityId) {
        if (requiredParams.isEmpty()) return;

        List<String> missingParams = requiredParams.stream()
                .filter(target -> {
                    String cleanTarget = cleanTarget(target);
                    // 检查flatVars（包含null值）
                    if (!flatVars.containsKey(cleanTarget)) {
                        return true;
                    }
                    // 值为null也算缺失（必填项不允许null）
                    return flatVars.get(cleanTarget) == null;
                })
                .collect(Collectors.toList());

        if (!missingParams.isEmpty()) {
            String errorMsg = String.format("❌ 活动 %s 的必填参数缺失或为空: %s", activityId, missingParams);
            log.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
    }

    private Object convertByDataType(Object value, DataMapping.InputMapping input) {
        if (value == null) return null;

        String dataType = input.getDataType();
        String beanClass = input.getBeanClass();

        if ("set".equalsIgnoreCase(dataType)) {
            return convertToContainer(value, "set", beanClass);
        } else if ("list".equalsIgnoreCase(dataType)) {
            return convertToContainer(value, "list", beanClass);
        } else if ("map".equalsIgnoreCase(dataType)) {
            return value;
        } else if ("bean".equalsIgnoreCase(dataType) && beanClass != null) {
            try {
                Class<?> clazz = Class.forName(beanClass);
                return objectMapper.convertValue(value, clazz);
            } catch (Exception e) {
                log.error("Bean类型转换失败: {}", beanClass, e);
                throw new RuntimeException("Bean转换失败: " + beanClass, e);
            }
        }
        return value;
    }

    private Object convertToContainer(Object value, String containerType, String elementClassName) {
        if (value == null) return null;
        try {
            Class<?> elementType = elementClassName != null ? Class.forName(elementClassName) : Object.class;
            Collection<Object> collection;
            if ("set".equalsIgnoreCase(containerType)) {
                collection = new LinkedHashSet<>();
            } else {
                collection = new ArrayList<>();
            }
            if (value instanceof Collection) {
                Collection<?> sourceColl = (Collection<?>) value;
                for (Object item : sourceColl) {
                    collection.add(convertElement(item, elementType));
                }
            } else if (value.getClass().isArray()) {
                Object[] array = (Object[]) value;
                for (Object item : array) {
                    collection.add(convertElement(item, elementType));
                }
            } else {
                collection.add(convertElement(value, elementType));
            }
            return collection;
        } catch (ClassNotFoundException e) {
            log.error("元素类不存在: {}", elementClassName, e);
            throw new RuntimeException("元素类加载失败: " + elementClassName, e);
        }
    }

    private Object convertElement(Object element, Class<?> targetType) {
        if (element == null) return null;
        if (targetType.isAssignableFrom(element.getClass())) return element;

        if (targetType == Long.class || targetType == long.class) {
            return element instanceof Number ? ((Number) element).longValue() : Long.parseLong(element.toString());
        } else if (targetType == Integer.class || targetType == int.class) {
            return element instanceof Number ? ((Number) element).intValue() : Integer.parseInt(element.toString());
        } else if (targetType == String.class) {
            return element.toString();
        } else if (targetType == Double.class || targetType == double.class) {
            return element instanceof Number ? ((Number) element).doubleValue() : Double.parseDouble(element.toString());
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(element.toString());
        }

        try {
            return objectMapper.convertValue(element, targetType);
        } catch (Exception e) {
            log.warn("元素类型转换失败，使用toString: {} -> {}", element.getClass().getSimpleName(), targetType.getSimpleName());
            return element.toString();
        }
    }

    private Map<String, Object> collectExistingBeans(DataMapping dataMapping, ActivityInstance activityInst) {
        Map<String, Object> existingBeans = new HashMap<>();
        ExecutionContext context = activityInst.getProcessInst().getCurrentContext();
        for (DataMapping.InputMapping input : dataMapping.getInputs()) {
            String target = cleanTarget(input.getTarget());
            String rootTarget = target.contains(".") ? target.substring(0, target.indexOf('.')) : target;
            Object bean = context.getProcessInstance().getVariable(rootTarget);
            if (bean != null && !(bean instanceof Map) && !(bean instanceof String)) {
                existingBeans.put(rootTarget, bean);
                log.debug("收集到已存在的Bean: {} = {}", rootTarget, bean.getClass().getSimpleName());
            }
        }
        return existingBeans;
    }

    private void processNestedProperty(Object value, String target,
                                       Map<String, ObjectWrapper> objectWrappers,
                                       Map<String, Object> pureFlatVariables,
                                       Map<String, Object> existingBeans,
                                       ActivityInstance activityInst) {
        String cleanTarget = cleanTarget(target);
        String[] parts = cleanTarget.split("\\.", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("非法嵌套target格式: " + target);
        }
        String objectName = parts[0];
        String propertyPath = parts[1];

        Object parentObj = existingBeans.get(objectName);
        if (parentObj == null) {
            parentObj = pureFlatVariables.get(objectName);
        }
        if (parentObj == null) {
            parentObj = activityInst.getProcessInst().getVariable(objectName);
        }

        // 如果父对象存在且不是Map，直接反射设置
        if (parentObj != null && !(parentObj instanceof Map)) {
            try {
                Field field = findRecursivelyField(parentObj.getClass(), propertyPath);
                if (field != null) {
                    field.setAccessible(true);
                    Object convertedValue = convertValueForField(value, field);
                    field.set(parentObj, convertedValue);
                    log.debug("直接设置Bean属性: {}.{} = {}", objectName, propertyPath, convertedValue);
                    if (!pureFlatVariables.containsKey(objectName)) {
                        pureFlatVariables.put(objectName, parentObj);
                    }
                    return;
                }
            } catch (Exception e) {
                log.warn("设置Bean属性失败，降级到ObjectWrapper: {}.{}", objectName, propertyPath, e);
            }
        }

        // 使用ObjectWrapper处理
        ObjectWrapper wrapper = objectWrappers.computeIfAbsent(objectName, k -> new ObjectWrapper());
        wrapper.addProperty(propertyPath, value);
        log.debug("注册嵌套属性到ObjectWrapper: {}.{} = {}", objectName, propertyPath, value);
    }

    private Object convertValueForField(Object value, Field field) {
        if (value == null) return null;
        Class<?> fieldType = field.getType();
        if (fieldType.isAssignableFrom(value.getClass())) return value;
        if (Set.class.isAssignableFrom(fieldType)) {
            return convertToSet(value, field);
        }
        if (List.class.isAssignableFrom(fieldType)) {
            if (value instanceof List) return value;
            if (value.getClass().isArray()) return Arrays.asList((Object[]) value);
            return Collections.singletonList(value);
        }
        if (fieldType == Long.class || fieldType == long.class) {
            return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
        } else if (fieldType == Integer.class || fieldType == int.class) {
            return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
        } else if (fieldType == Boolean.class || fieldType == boolean.class) {
            return Boolean.parseBoolean(value.toString());
        } else if (fieldType == String.class) {
            return value.toString();
        }
        return value;
    }

    private Set<?> convertToSet(Object value, Field field) {
        Set<Object> resultSet = new LinkedHashSet<>();
        Class<?> elementType = Object.class;
        try {
            java.lang.reflect.ParameterizedType genericType = (java.lang.reflect.ParameterizedType) field.getGenericType();
            java.lang.reflect.Type[] typeArgs = genericType.getActualTypeArguments();
            if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                elementType = (Class<?>) typeArgs[0];
            }
        } catch (Exception e) {
            log.debug("无法获取Set泛型类型");
        }
        if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            for (Object item : collection) {
                resultSet.add(convertElement(item, elementType));
            }
        } else if (value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            for (Object item : array) {
                resultSet.add(convertElement(item, elementType));
            }
        } else {
            resultSet.add(convertElement(value, elementType));
        }
        return resultSet;
    }

    private void mergeObjectWrappers(Map<String, Object> finalResult,
                                     Map<String, ObjectWrapper> objectWrappers,
                                     Map<String, Object> pureFlatVariables,
                                     Map<String, String> beanClassMap) {
        for (Map.Entry<String, ObjectWrapper> entry : objectWrappers.entrySet()) {
            String objectName = entry.getKey();
            // 如果已经有Bean对象（非Map），跳过ObjectWrapper构建
            if (pureFlatVariables.containsKey(objectName) &&
                    !(pureFlatVariables.get(objectName) instanceof Map) &&
                    !(pureFlatVariables.get(objectName) instanceof Collection)) {
                log.debug("跳过ObjectWrapper，已有实例化对象: {}", objectName);
                continue;
            }
            ObjectWrapper wrapper = entry.getValue();
            Object builtObject = wrapper.build();
            if (builtObject != null) {
                finalResult.put(objectName, builtObject);
            }
        }
        finalResult.putAll(pureFlatVariables);
    }

    private String cleanTarget(String target) {
        if (target == null) return "";
        String clean = target.trim();
        if (clean.startsWith("#")) clean = clean.substring(1);
        if (clean.startsWith("workflow.") || clean.startsWith("local.") || clean.startsWith("package.")) {
            clean = clean.substring(clean.indexOf('.') + 1);
        }
        return clean;
    }

    private static Field findRecursivelyField(Class<?> clazz, String fieldName) {
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                // 继续父类查找
            }
        }
        return null;
    }

    private static class ObjectWrapper {
        private final Map<String, Object> properties = new LinkedHashMap<>();
        private String className;

        public void addProperty(String path, Object value) {
            properties.put(path, value);
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public Object build() {
            if (className == null) {
                return new LinkedHashMap<>(properties);
            }
            try {
                Class<?> clazz = Class.forName(className);
                Object instance = clazz.newInstance();
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    Field field = findRecursivelyField(clazz, entry.getKey());
                    if (field != null) {
                        field.setAccessible(true);
                        Object value = entry.getValue();
                        if (value != null && field.getType().isAssignableFrom(value.getClass())) {
                            field.set(instance, value);
                        } else if (value != null) {
                            value = convertValueForField(value, field);
                            field.set(instance, value);
                        } else {
                            field.set(instance, null);
                        }
                    }
                }
                return instance;
            } catch (Exception e) {
                throw new RuntimeException("构建对象失败: " + className, e);
            }
        }

        private Object convertValueForField(Object value, Field field) {
            Class<?> fieldType = field.getType();
            if (Set.class.isAssignableFrom(fieldType)) {
                Set<Object> set = new LinkedHashSet<>();
                if (value instanceof Collection) {
                    set.addAll((Collection<?>) value);
                } else if (value != null) {
                    set.add(value);
                }
                return set;
            }
            if (List.class.isAssignableFrom(fieldType)) {
                List<Object> list = new ArrayList<>();
                if (value instanceof Collection) {
                    list.addAll((Collection<?>) value);
                } else if (value != null) {
                    list.add(value);
                }
                return list;
            }
            if (fieldType == Long.class || fieldType == long.class) {
                return value instanceof Number ? ((Number) value).longValue() :
                        (value != null ? Long.parseLong(value.toString()) : null);
            } else if (fieldType == Integer.class || fieldType == int.class) {
                return value instanceof Number ? ((Number) value).intValue() :
                        (value != null ? Integer.parseInt(value.toString()) : null);
            }
            return value;
        }
    }
}