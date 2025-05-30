package com.example.chat_app.controller;

import com.example.chat_app.config.RedisMessagePublisher;
import com.example.chat_app.model.ChatMessage;
import com.example.chat_app.model.ChatRoom;
import com.example.chat_app.model.User;
import com.example.chat_app.repository.ChatRoomRepository;
import com.example.chat_app.repository.UserRepository;
import com.example.chat_app.service.JwtService;
import com.example.chat_app.service.MessageService;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.lettuce.core.pubsub.PubSubOutput.Type.message;

@Controller
public class ChatController {

    private final RedisMessagePublisher redisPublisher;
    private final MessageService messageService;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Map<String, Set<String>> onlineUsersByRoom = new ConcurrentHashMap<>();
    @Value("${upload.path}")
    private String uploadPath;

    public ChatController(RedisMessagePublisher redisPublisher, MessageService messageService,
                          ChatRoomRepository chatRoomRepository, UserRepository userRepository,
                          BCryptPasswordEncoder passwordEncoder, JwtService jwtService) {
        this.redisPublisher = redisPublisher;
        this.messageService = messageService;
        this.chatRoomRepository = chatRoomRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @MessageMapping("/sendMessage/{roomId}")
    public void sendMessage(@Payload ChatMessage chatMessage, @DestinationVariable String roomId) {
        chatMessage.setTimestamp(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new Date(System.currentTimeMillis())));
        chatMessage.setRoomId(roomId);
        chatMessage.setId(UUID.randomUUID().toString());
        if (chatMessage.getMediaType() == null) {
            chatMessage.setMediaType("TEXT");
        }
        try {
            messageService.saveMessage(chatMessage);
            System.out.println("Publishing message to /topic/messages/" + roomId + ": " + chatMessage.toString());
            redisPublisher.publish(chatMessage, roomId);
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @MessageMapping("/typing/{roomId}")
    public void typing(@Payload ChatMessage typingEvent, @DestinationVariable String roomId) {
        redisPublisher.publishTyping(typingEvent, roomId);
    }

    @MessageMapping("/reaction/{roomId}")
    public void reaction(@Payload Map<String, String> reactionEvent, @DestinationVariable String roomId) {
        redisPublisher.publishReaction(reactionEvent, roomId);
    }

    @MessageMapping("/join/{roomId}")
    public void join(@Payload ChatMessage joinEvent, @DestinationVariable String roomId) {
        String username = joinEvent.getSender();
        onlineUsersByRoom.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(username);
        System.out.println("User joined room " + roomId + ": " + username);
        redisPublisher.publishUserList(new ArrayList<>(onlineUsersByRoom.get(roomId)), roomId);
    }

    @MessageMapping("/leave/{roomId}")
    public void leave(@Payload ChatMessage leaveEvent, @DestinationVariable String roomId) {
        String username = leaveEvent.getSender();
        Set<String> users = onlineUsersByRoom.getOrDefault(roomId, ConcurrentHashMap.newKeySet());
        users.remove(username);
        if (users.isEmpty()) {
            onlineUsersByRoom.remove(roomId);
        } else {
            onlineUsersByRoom.put(roomId, users);
        }
        redisPublisher.publishUserList(new ArrayList<>(onlineUsersByRoom.getOrDefault(roomId, Collections.emptySet())), roomId);
    }

    @PostMapping("/upload")
    @ResponseBody
    public String uploadFile(@RequestParam("file") MultipartFile file, @RequestParam String username, @RequestParam String roomId) throws Exception {
        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        File targetFile = new File(uploadPath + fileName);
        FileUtils.copyInputStreamToFile(file.getInputStream(), targetFile);

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSender(username);
        chatMessage.setRoomId(roomId);
        chatMessage.setTimestamp(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        chatMessage.setId(UUID.randomUUID().toString());
        chatMessage.setMediaUrl("/uploads/" + fileName);
        chatMessage.setMediaType(file.getContentType().startsWith("image") ? "IMAGE" : "VIDEO");
        messageService.saveMessage(chatMessage);
        redisPublisher.publish(chatMessage, roomId);
        return chatMessage.getMediaUrl();
    }

    @GetMapping("/history/{roomId}")
    @ResponseBody
    public List<ChatMessage> getHistory(@PathVariable String roomId) {
        return messageService.getMessagesByRoomId(roomId);
    }

//    @DeleteMapping("/message/{roomId}/{messageId}")
//    @ResponseBody
//    public void deleteMessage(@PathVariable String roomId, @PathVariable String messageId) {
//        messageService.deleteMessage(messageId, roomId);
//        // Optionally notify clients via WebSocket
//        Map<String, String> event = new HashMap<>();
//        event.put("messageId", messageId);
//        redisPublisher.publishReaction(event, roomId); // Using reaction channel for deletion event
//    }

    @GetMapping("/")
    public String index() {
        return "redirect:/dashboard.html";
    }

    @GetMapping("/chat/{roomId}")
    public String joinRoom(@PathVariable String roomId) {
        return "redirect:/index.html?roomId=" + roomId;
    }

    @GetMapping("/dashboard")
    @ResponseBody
    public String dashboard() throws IOException {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource("static/dashboard.html").getInputStream(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            return "Error: dashboard.html not found";
        }
    }

    @GetMapping("/api/rooms")
    @ResponseBody
    public List<ChatRoom> getAllRooms() {
        return chatRoomRepository.findAll();
    }

    @PostMapping("/api/rooms")
    @ResponseBody
    public ChatRoom createRoom(@RequestBody Map<String, String> roomData, @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String username = jwtService.extractUsername(token);

        String roomName = roomData.get("name");
        if (roomName == null || roomName.trim().isEmpty()) {
            throw new RuntimeException("Room name cannot be empty");
        }

        if (chatRoomRepository.findByName(roomName).isPresent()) {
            throw new RuntimeException("Room name '" + roomName + "' already exists");
        }

        ChatRoom chatRoom = new ChatRoom(roomName, username);
        chatRoomRepository.save(chatRoom);
        return chatRoom;
    }

    @GetMapping("/login")
    public String login() {
        return "redirect:/login.html";
    }

    @GetMapping("/register")
    @ResponseBody
    public String register() throws IOException {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource("static/register.html").getInputStream(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            return "Error: register.html not found";
        }
    }

    @PostMapping("/register")
    @ResponseBody
    public String registerUser(@RequestBody User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
        return jwtService.generateToken(user.getUsername());
    }

    @PostMapping("/login")
    @ResponseBody
    public String login(@RequestBody User user) {
        User existingUser = userRepository.findByUsername(user.getUsername()).orElseThrow(() -> new RuntimeException("User not found"));
        if (passwordEncoder.matches(user.getPassword(), existingUser.getPassword())) {
            return jwtService.generateToken(user.getUsername());
        }
        throw new RuntimeException("Invalid credentials");
    }
}