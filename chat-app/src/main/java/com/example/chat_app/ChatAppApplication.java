package com.example.chat_app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;

@SpringBootApplication(exclude = {RedisRepositoriesAutoConfiguration.class})
public class ChatAppApplication {
	public static void main(String[] args) {
		SpringApplication.run(ChatAppApplication.class, args);
	}
}