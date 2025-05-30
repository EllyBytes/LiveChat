package com.example.chat_app.repository;

import com.example.chat_app.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {
    List<ChatMessage> findByRoomId(String roomId);
    List<ChatMessage> findByRoomIdOrderByTimestampAsc(String roomId);
}
