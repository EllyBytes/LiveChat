package com.example.chat_app.service;

import com.example.chat_app.model.ChatMessage;
import com.example.chat_app.repository.ChatMessageRepository;
import com.google.gson.Gson;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private static final String KEY_PREFIX = "messages:roomId:";
    private static final long EXPIRY_SECONDS = 24 * 60 * 60;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final Timer redisFetchTimer;
    private final Timer mysqlFetchTimer;
    private final Gson gson;

    public MessageService(MeterRegistry meterRegistry) {
        this.gson = new Gson();
        this.redisFetchTimer = Timer.builder("chat.message.fetch.redis")
                .description("Time taken to fetch messages from Redis")
                .register(meterRegistry);
        this.mysqlFetchTimer = Timer.builder("chat.message.fetch.mysql")
                .description("Time taken to fetch messages from MySQL")
                .register(meterRegistry);
    }

    public void saveMessage(ChatMessage message) {
        try {
            messageRepository.save(message);
        } catch (Exception e) {
            System.err.println("Failed to save message to MySQL: " + e.getMessage());
            throw e;
        }

        String key = KEY_PREFIX + message.getRoomId();
        String messageJson = gson.toJson(message);
        redisTemplate.opsForList().rightPush(key, messageJson);
        redisTemplate.expire(key, EXPIRY_SECONDS, TimeUnit.SECONDS);
    }

    public List<ChatMessage> getMessagesByRoomId(String roomId) {
        String key = KEY_PREFIX + roomId;
        List<ChatMessage> messages = new ArrayList<>();

        // Redis fetch with timing
        Timer.Sample redisSample = Timer.start();
        List<String> messageJsons = redisTemplate.opsForList().range(key, 0, -1);
        redisSample.stop(redisFetchTimer);

        if (messageJsons != null && !messageJsons.isEmpty()) {
            System.out.println("Fetched " + messageJsons.size() + " messages from Redis for roomId: " + roomId);
            return messageJsons.stream()
                    .map(json -> gson.fromJson(json, ChatMessage.class))
                    .collect(Collectors.toList());
        }

        // MySQL fetch with timing
        System.out.println("No messages in Redis for roomId: " + roomId + ", fetching from MySQL");
        Timer.Sample mysqlSample = Timer.start();
        messages = messageRepository.findByRoomId(roomId);
        mysqlSample.stop(mysqlFetchTimer);

        if (messages != null && !messages.isEmpty()) {
            System.out.println("Fetched " + messages.size() + " messages from MySQL for roomId: " + roomId);
            messages.forEach(message -> {
                String messageJson = gson.toJson(message);
                redisTemplate.opsForList().rightPush(key, messageJson);
            });
            redisTemplate.expire(key, EXPIRY_SECONDS, TimeUnit.SECONDS);
        }

        return messages != null ? messages : new ArrayList<>();
    }
}
