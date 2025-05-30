package com.example.chat_app.config;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisMessageSubscriber {

    private final SimpMessagingTemplate messagingTemplate;

    public RedisMessageSubscriber(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void onMessage(Object message, String channel) {
        if (channel.endsWith(":typing")) {
            messagingTemplate.convertAndSend("/topic/typing/" + channel.split(":")[1], message);
        } else if (channel.endsWith(":reactions")) {
            messagingTemplate.convertAndSend("/topic/reactions/" + channel.split(":")[1], message);
        } else if (channel.endsWith(":users")) {
            messagingTemplate.convertAndSend("/topic/users/" + channel.split(":")[1], message);
        } else {
            messagingTemplate.convertAndSend("/topic/messages/" + channel.split(":")[1], message);
        }
    }
}