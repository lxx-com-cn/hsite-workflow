package com.hbs.site.module.bfm.engine.invoker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消息生产者 - 简单实现
 */
@Slf4j
@Component
public class MessageProducer {

    public void sendMessage(Map<String, Object> message) {
        log.info("发送消息: channel={}, topic={}, data={}",
                message.get("channel"), message.get("topic"), message.get("data"));
    }

    public void sendMessage(String channel, String topic, Object data) {
        log.info("发送消息到通道: channel={}, topic={}, data={}", channel, topic, data);
    }

    public void sendMessageAsync(Map<String, Object> message) {
        new Thread(() -> {
            try {
                sendMessage(message);
            } catch (Exception e) {
                log.error("异步发送消息失败", e);
            }
        }).start();
    }
}