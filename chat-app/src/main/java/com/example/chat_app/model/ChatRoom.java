package com.example.chat_app.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
public class ChatRoom {
    @Id
    private String id;
    private String name;
    private String creator;

    public ChatRoom() {
        this.id = UUID.randomUUID().toString();
    }

    public ChatRoom(String name, String creator) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.creator = creator;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }
}