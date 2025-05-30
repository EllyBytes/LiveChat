package com.example.chat_app.config;

import com.example.chat_app.service.JwtService;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketSecurityConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;

    public WebSocketSecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    List<String> authorization = accessor.getNativeHeader("Authorization");
                    if (authorization != null && !authorization.isEmpty()) {
                        String token = authorization.get(0).replace("Bearer ", "");
                        String username = jwtService.extractUsername(token);
                        if (username != null && jwtService.validateToken(token, username)) {
                            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                    username, null, java.util.Collections.emptyList());
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            accessor.setUser(authentication);
                        } else {
                            throw new IllegalArgumentException("Invalid or expired JWT token");
                        }
                    } else {
                        throw new IllegalArgumentException("Missing Authorization header");
                    }
                }
                return message;
            }
        });
    }
}