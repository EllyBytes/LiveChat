package com.example.chat_app.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class ChatMessage {
    @Id
    private String id;
    private String sender;
    private String content;
    private String roomId;
    private String timestamp;
    private String mediaUrl;
    private String mediaType;

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    @Override
    public String toString() {
        return "ChatMessage{id='" + id + "', sender='" + sender + "', content='" + content + "', roomId='" + roomId + "', timestamp='" + timestamp + "'}";
    }
}