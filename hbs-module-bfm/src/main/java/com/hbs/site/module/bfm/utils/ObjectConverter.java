package com.hbs.site.module.bfm.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 对象转换工具类
 */
@Slf4j
@Component
public class ObjectConverter {

    private final ObjectMapper objectMapper;

    public ObjectConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将Map转换为指定类型的对象
     */
    @SuppressWarnings("unchecked")
    public <T> T convertMapToBean(Map<String, Object> map, Class<T> targetType) {
        if (map == null || targetType == null) {
            return null;
        }

        try {
            // 首先尝试使用Jackson转换
            return objectMapper.convertValue(map, targetType);
        } catch (Exception e) {
            log.debug("Jackson转换失败，尝试BeanUtils: {}", targetType.getName());
            try {
                // 使用BeanUtils
                T instance = BeanUtils.instantiateClass(targetType);
                BeanUtils.copyProperties(map, instance);
                return instance;
            } catch (Exception e2) {
                log.error("BeanUtils转换失败: {}", targetType.getName(), e2);
                // 最后尝试使用反射
                return convertByReflection(map, targetType);
            }
        }
    }

    /**
     * 使用反射进行对象转换
     */
    private <T> T convertByReflection(Map<String, Object> map, Class<T> targetType) {
        try {
            T instance = targetType.newInstance();

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String fieldName = entry.getKey();
                Object value = entry.getValue();

                try {
                    java.lang.reflect.Field field = targetType.getDeclaredField(fieldName);
                    field.setAccessible(true);

                    // 类型转换
                    if (value != null && !field.getType().isAssignableFrom(value.getClass())) {
                        value = convertValueType(value, field.getType());
                    }

                    field.set(instance, value);
                } catch (NoSuchFieldException e) {
                    log.debug("字段不存在，跳过: {}.{}", targetType.getName(), fieldName);
                }
            }

            return instance;
        } catch (Exception e) {
            log.error("反射转换失败: {}", targetType.getName(), e);
            throw new RuntimeException("无法将Map转换为对象: " + targetType.getName(), e);
        }
    }

    /**
     * 转换值类型
     */
    private Object convertValueType(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        if (targetType == String.class) {
            return value.toString();
        } else if (targetType == Long.class || targetType == long.class) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            } else if (value instanceof String) {
                return Long.parseLong((String) value);
            }
        } else if (targetType == Integer.class || targetType == int.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                return Integer.parseInt((String) value);
            }
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Boolean) {
                return value;
            } else if (value instanceof String) {
                return Boolean.parseBoolean((String) value);
            }
        }

        return value;
    }

    /**
     * 提取嵌套Map结构中的内层Map
     */
    public Map<String, Object> extractInnerMap(Map<String, Object> outerMap) {
        if (outerMap == null || outerMap.isEmpty()) {
            return outerMap;
        }

        // 如果只有一个key，且value是Map，返回内层Map
        if (outerMap.size() == 1) {
            Object firstValue = outerMap.values().iterator().next();
            if (firstValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> innerMap = (Map<String, Object>) firstValue;
                return innerMap;
            }
        }

        return outerMap;
    }
}