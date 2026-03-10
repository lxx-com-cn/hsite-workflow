package com.hbs.site.module.bfm.engine.invoker;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbs.site.module.bfm.data.define.*;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.engine.expression.ExpressionEvaluator;
import com.hbs.site.module.bfm.engine.subprocess.TxModeHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 调用分发器 - 增强版（修复参数类型转换和方法查找）
 */
@Slf4j
@Component
public class InvokerDispatcher {

    private final ApplicationContext applicationContext;
    private final RestTemplate restTemplate;
    private final MessageProducer messageProducer;
    private final ObjectMapper objectMapper;
    private final ExpressionEvaluator expressionEvaluator;

    public InvokerDispatcher(ApplicationContext applicationContext,
                             RestTemplate restTemplate,
                             MessageProducer messageProducer,
                             ObjectMapper objectMapper,
                             ExpressionEvaluator expressionEvaluator) {
        this.applicationContext = applicationContext;
        this.restTemplate = restTemplate;
        this.messageProducer = messageProducer;
        this.objectMapper = objectMapper;
        this.expressionEvaluator = expressionEvaluator;

        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        this.objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);

        log.warn("InvokerDispatcher初始化完成（增强参数类型转换版）");
    }

    public Object invokeSpringBean(String beanName, String methodName, Object[] args) {
        boolean isTxMode = TxModeHolder.isTxMode();
        int maxAttempts = isTxMode ? 1 : 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return tryInvokeSpringBean(beanName, methodName, args);
            } catch (Exception e) {
                Throwable realEx = e;
                if (e instanceof java.lang.reflect.InvocationTargetException) {
                    realEx = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
                }

                if (attempt == maxAttempts) {
                    String errorMsg = String.format("Spring Bean调用失败: %s.%s, error=%s",
                            beanName, methodName, realEx.getMessage());
                    log.error("❌ {}", errorMsg, realEx);
                    throw new RuntimeException(errorMsg, realEx);
                }

                log.warn("Spring Bean调用失败，重试 {}/{}: bean={}, method={}, error={}",
                        attempt, maxAttempts, beanName, methodName, realEx.getMessage());
                try {
                    Thread.sleep(100L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试等待被中断", ie);
                }
            }
        }
        throw new RuntimeException("Spring Bean调用失败，重试耗尽");
    }

    private Object tryInvokeSpringBean(String beanName, String methodName, Object[] args) throws Exception {
        Object bean = applicationContext.getBean(beanName);
        if (bean == null) {
            throw new IllegalArgumentException("Spring Bean不存在: " + beanName);
        }

        Class<?> targetClass = getTargetClass(bean);
        log.debug("查找Spring Bean方法: bean={}, targetClass={}, method={}, argsCount={}",
                beanName, targetClass.getName(), methodName, args != null ? args.length : 0);

        // 关键修复：增强方法查找，支持更灵活的类型匹配
        Method method = findBestMatchingMethod(targetClass, methodName, args);
        if (method == null) {
            throw new IllegalArgumentException(
                    String.format("找不到可调用方法: %s.%s (参数: %d个, 类型: %s)%n可用方法:%s",
                            beanName, methodName, args != null ? args.length : 0,
                            Arrays.toString(args != null ? args : new Object[0]),
                            getAvailableMethods(targetClass, methodName))
            );
        }

        // 转换参数以匹配方法参数类型
        Object[] invokeArgs = convertArgumentsForMethod(method, args);
        log.debug("转换后的参数: {}", Arrays.toString(invokeArgs));

        try {
            Object result = method.invoke(bean, invokeArgs);
            log.info("✅ Spring Bean调用成功: {}.{}, resultType={}, result={}",
                    beanName, methodName,
                    result != null ? result.getClass().getName() : "null",
                    result);
            return result;
        } catch (Exception e) {
            log.error("Spring Bean调用失败: {}.{}, error={}", beanName, methodName, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 关键修复：查找最佳匹配方法
     * 支持：精确匹配、类型转换匹配、单参数VO对象匹配
     */
    private Method findBestMatchingMethod(Class<?> targetClass, String methodName, Object[] args) {
        int argCount = args != null ? args.length : 0;
        Method[] methods = targetClass.getMethods();

        log.debug("查找最佳匹配方法: class={}, methodName={}, argCount={}",
                targetClass.getName(), methodName, argCount);

        // 收集所有候选方法
        List<Method> candidates = new ArrayList<>();

        for (Method method : methods) {
            if (!method.getName().equals(methodName)) continue;

            int paramCount = method.getParameterCount();
            boolean isVarArgs = method.isVarArgs();

            // 参数数量检查
            if (isVarArgs) {
                if (argCount < paramCount - 1) continue;
            } else {
                if (paramCount != argCount) continue;
            }

            candidates.add(method);
        }

        if (candidates.isEmpty()) {
            log.error("无候选方法: {}.{}, 参数数量: {}", targetClass.getName(), methodName, argCount);
            return null;
        }

        // 第一轮：精确类型匹配
        for (Method method : candidates) {
            if (isExactMatch(method, args)) {
                log.debug("找到精确匹配方法: {}", method);
                return method;
            }
        }

        // 第二轮：可转换类型匹配（最宽松的匹配）
        for (Method method : candidates) {
            if (isConvertibleMatch(method, args)) {
                log.debug("找到可转换匹配方法: {}", method);
                return method;
            }
        }

        // 第三轮：如果有候选方法，选择第一个（通常只有一个同名方法）
        if (!candidates.isEmpty()) {
            Method fallback = candidates.get(0);
            log.warn("使用首个候选方法作为降级: {}", fallback);
            return fallback;
        }

        return null;
    }

    /**
     * 精确类型匹配
     */
    private boolean isExactMatch(Method method, Object[] args) {
        Class<?>[] paramTypes = method.getParameterTypes();
        int argCount = args != null ? args.length : 0;

        for (int i = 0; i < argCount; i++) {
            Class<?> paramType = getParamType(method, i);
            Object arg = args[i];

            if (arg == null) {
                if (paramType.isPrimitive()) return false;
                continue;
            }

            if (!paramType.isAssignableFrom(arg.getClass())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 可转换类型匹配
     */
    private boolean isConvertibleMatch(Method method, Object[] args) {
        Class<?>[] paramTypes = method.getParameterTypes();
        int argCount = args != null ? args.length : 0;

        for (int i = 0; i < argCount; i++) {
            Class<?> paramType = getParamType(method, i);
            Object arg = args[i];

            if (arg == null) {
                if (paramType.isPrimitive()) return false;
                continue;
            }

            // 类型已匹配
            if (paramType.isAssignableFrom(arg.getClass())) {
                continue;
            }

            // 检查是否可以转换
            if (!canConvert(arg, paramType)) {
                return false;
            }
        }
        return true;
    }

    private Class<?> getParamType(Method method, int index) {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (method.isVarArgs() && index >= paramTypes.length - 1) {
            return paramTypes[paramTypes.length - 1].getComponentType();
        }
        return paramTypes[index];
    }

    private boolean canConvert(Object value, Class<?> targetType) {
        if (value == null) return !targetType.isPrimitive();

        // String转换
        if (targetType == String.class) return true;

        // 数字类型转换
        if ((targetType == Long.class || targetType == long.class) &&
                (value instanceof Number || value instanceof String)) return true;
        if ((targetType == Integer.class || targetType == int.class) &&
                (value instanceof Number || value instanceof String)) return true;

        // 使用ObjectMapper尝试转换
        try {
            objectMapper.convertValue(value, targetType);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Object[] convertArgumentsForMethod(Method method, Object[] args) {
        Class<?>[] paramTypes = method.getParameterTypes();
        boolean isVarArgs = method.isVarArgs();
        int argCount = args != null ? args.length : 0;

        Object[] result = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> paramType = paramTypes[i];

            if (isVarArgs && i == paramTypes.length - 1) {
                result[i] = handleVarArgs(paramType, args, i);
            } else if (i < argCount) {
                result[i] = convertValue(args[i], paramType);
            } else {
                result[i] = getDefaultValue(paramType);
            }
        }

        return result;
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return getDefaultValue(targetType);
        }

        // 类型已匹配
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        // String转换
        if (targetType == String.class) {
            return value.toString();
        }

        // 数字类型转换
        if (targetType == Long.class || targetType == long.class) {
            if (value instanceof Number) return ((Number) value).longValue();
            if (value instanceof String) return Long.parseLong((String) value);
        }
        if (targetType == Integer.class || targetType == int.class) {
            if (value instanceof Number) return ((Number) value).intValue();
            if (value instanceof String) return Integer.parseInt((String) value);
        }

        // 使用ObjectMapper
        try {
            return objectMapper.convertValue(value, targetType);
        } catch (Exception e) {
            log.warn("ObjectMapper转换失败: {} -> {}, 使用原值",
                    value.getClass().getSimpleName(), targetType.getSimpleName());
            return value;
        }
    }

    private Object handleVarArgs(Class<?> paramType, Object[] args, int startIndex) {
        Class<?> componentType = paramType.getComponentType();
        int varArgCount = args.length - startIndex;

        Object array = Array.newInstance(componentType, varArgCount);
        for (int i = 0; i < varArgCount; i++) {
            Array.set(array, i, convertValue(args[startIndex + i], componentType));
        }
        return array;
    }

    private Object getDefaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return 0;
    }

    private Class<?> getTargetClass(Object bean) {
        Class<?> clazz = bean.getClass();

        if (clazz.getName().contains("$$EnhancerBySpringCGLIB$$") ||
                clazz.getName().contains("$$FastClassBySpringCGLIB$$")) {
            Class<?> superClass = clazz.getSuperclass();
            log.debug("检测到CGLIB代理类: proxy={}, target={}", clazz.getName(), superClass.getName());
            return superClass;
        }

        if (clazz.getName().contains("$Proxy")) {
            Class<?>[] interfaces = clazz.getInterfaces();
            if (interfaces.length > 0) {
                log.debug("检测到JDK代理类: proxy={}, targetInterface={}", clazz.getName(), interfaces[0].getName());
                return interfaces[0];
            }
        }

        return clazz;
    }

    private String getAvailableMethods(Class<?> clazz, String methodName) {
        StringBuilder sb = new StringBuilder();
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(methodName)) {
                sb.append("\n  - ").append(method.getName())
                        .append("(").append(Arrays.toString(method.getParameterTypes())).append(")");
            }
        }
        return sb.length() > 0 ? sb.toString() : "\n  - 无";
    }

    // 其他方法保持不变...
    public Object invokeRest(RestConfig restConfig, Map<String, Object> requestData) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            if (restConfig.getHeaders() != null) {
                String[] headerPairs = restConfig.getHeaders().split(",");
                for (String pair : headerPairs) {
                    String[] keyValue = pair.split(":", 2);
                    if (keyValue.length == 2) {
                        headers.add(keyValue[0].trim(), keyValue[1].trim());
                    }
                }
            }

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestData, headers);
            String method = restConfig.getMethod().toUpperCase();
            ResponseEntity<String> response;

            log.info("调用REST: method={}, endpoint={}, data={}", method, restConfig.getEndpoint(), requestData);

            switch (method) {
                case "GET":
                    response = restTemplate.exchange(restConfig.getEndpoint(), HttpMethod.GET, requestEntity, String.class);
                    break;
                case "POST":
                    response = restTemplate.postForEntity(restConfig.getEndpoint(), requestEntity, String.class);
                    break;
                case "PUT":
                    response = restTemplate.exchange(restConfig.getEndpoint(), HttpMethod.PUT, requestEntity, String.class);
                    break;
                case "DELETE":
                    response = restTemplate.exchange(restConfig.getEndpoint(), HttpMethod.DELETE, requestEntity, String.class);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的HTTP方法: " + method);
            }

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("REST调用成功: status={}, body={}", response.getStatusCode(), response.getBody());
                return response.getBody();
            } else {
                throw new RuntimeException("REST调用失败: " + response.getStatusCode() + ", body: " + response.getBody());
            }
        } catch (Exception e) {
            log.error("REST调用失败: endpoint={}", restConfig.getEndpoint(), e);
            throw new RuntimeException("REST调用失败: " + e.getMessage(), e);
        }
    }

    public Object invokeWebService(WebServiceConfig webServiceConfig, Map<String, Object> requestData) {
        log.debug("调用WebService: wsdl={}, operation={}", webServiceConfig.getWsdl(), webServiceConfig.getOperation());
        return "web-service-response-" + System.currentTimeMillis();
    }

    public Object invokeMessage(MessageConfig messageConfig, Map<String, Object> messageData) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("channel", messageConfig.getChannel());
            message.put("topic", messageConfig.getTopic());
            message.put("type", messageConfig.getMessageType());
            message.put("data", messageData);
            message.put("timestamp", System.currentTimeMillis());

            messageProducer.sendMessage(message);
            log.info("消息发送成功: channel={}, topic={}", messageConfig.getChannel(), messageConfig.getTopic());
            return "Message sent to channel: " + messageConfig.getChannel();
        } catch (Exception e) {
            log.error("消息发送失败: channel={}", messageConfig.getChannel(), e);
            throw new RuntimeException("消息发送失败: " + e.getMessage(), e);
        }
    }

    public Object invokeJavaBean(JavaBeanConfig javaConfig, Object[] args) {
        try {
            Class<?> clazz = Class.forName(javaConfig.getClassName());
            Method method = findMethodWithConversion(clazz, javaConfig.getMethod(), args);

            if (method == null) {
                throw new IllegalArgumentException(
                        String.format("JavaBean方法不存在: %s.%s (参数: %d个)",
                                javaConfig.getClassName(), javaConfig.getMethod(), args.length)
                );
            }

            Object[] invokeArgs = convertArgumentsForMethod(method, args);
            Object instance = Boolean.TRUE.equals(javaConfig.getIsStatic()) ? null : clazz.newInstance();

            Object result = method.invoke(instance, invokeArgs);
            log.info("✅ JavaBean调用成功: {}.{}, result={}",
                    javaConfig.getClassName(), javaConfig.getMethod(), result);
            return result;
        } catch (Exception e) {
            log.error("JavaBean调用失败: class={}, method={}", javaConfig.getClassName(), javaConfig.getMethod(), e);
            throw new RuntimeException("JavaBean调用失败: " + e.getMessage(), e);
        }
    }

    private Method findMethodWithConversion(Class<?> targetClass, String methodName, Object[] args) {
        int argCount = args != null ? args.length : 0;
        Method[] methods = targetClass.getMethods();

        log.debug("查找方法（支持类型转换）: class={}, methodName={}, argCount={}",
                targetClass.getName(), methodName, argCount);

        // 第一轮：精确匹配
        for (Method method : methods) {
            if (!method.getName().equals(methodName)) continue;
            if (isMethodMatch(method, args, true)) {
                log.debug("找到精确匹配方法: {}", method);
                return method;
            }
        }

        // 第二轮：允许类型转换的匹配
        for (Method method : methods) {
            if (!method.getName().equals(methodName)) continue;
            if (isMethodMatch(method, args, false)) {
                log.debug("找到可转换匹配方法: {}", method);
                return method;
            }
        }

        log.error("未找到匹配方法: {}.{}, 参数: {}", targetClass.getName(), methodName, argCount);
        return null;
    }

    private boolean isMethodMatch(Method method, Object[] args, boolean strict) {
        int argCount = args != null ? args.length : 0;
        boolean isVarArgs = method.isVarArgs();
        int paramCount = method.getParameterCount();

        // 参数数量检查
        if (isVarArgs) {
            if (argCount < paramCount - 1) return false;
        } else {
            if (paramCount != argCount) return false;
        }

        if (strict) {
            // 严格匹配：类型必须完全一致
            Class<?>[] paramTypes = method.getParameterTypes();
            for (int i = 0; i < argCount; i++) {
                Class<?> paramType = isVarArgs && i >= paramTypes.length - 1
                        ? paramTypes[paramTypes.length - 1].getComponentType()
                        : paramTypes[i];
                Object arg = args[i];

                if (arg == null) {
                    if (paramType.isPrimitive()) return false;
                    continue;
                }

                if (!paramType.isAssignableFrom(arg.getClass())) {
                    return false;
                }
            }
            return true;
        } else {
            // 宽松匹配：允许类型转换
            Class<?>[] paramTypes = method.getParameterTypes();
            for (int i = 0; i < argCount; i++) {
                Class<?> paramType = isVarArgs && i >= paramTypes.length - 1
                        ? paramTypes[paramTypes.length - 1].getComponentType()
                        : paramTypes[i];
                Object arg = args[i];

                if (arg == null) {
                    if (paramType.isPrimitive()) return false;
                    continue;
                }

                // 检查是否可以转换
                if (!paramType.isAssignableFrom(arg.getClass())) {
                    if (!canConvert(arg, paramType)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }
}