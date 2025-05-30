package com.example.chat_app.config;

import com.example.chat_app.model.ChatMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RedisMessagePublisher {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public RedisMessagePublisher(RedisTemplate<String, Object> redisTemplate, RedisTemplate<String, String> userTemplate) {
    }
    @Autowired
    private SimpMessagingTemplate messagingTemplate;


    public void publish(ChatMessage message, String roomId) {
        messagingTemplate.convertAndSend("/topic/messages/" + roomId, message);
        redisTemplate.convertAndSend("/topic/messages/" + roomId, message);
    }
    public void publishTyping(ChatMessage typingEvent, String roomId) {
        redisTemplate.convertAndSend("/topic/typing/" + roomId, typingEvent);
    }

    public void publishReaction(Map<String, String> reactionEvent, String roomId) {
        redisTemplate.convertAndSend("/topic/reactions/" + roomId, reactionEvent);
    }

    public void publishUserList(List<String> users, String roomId) {
        redisTemplate.convertAndSend("/topic/users/" + roomId, users);
    }
}
