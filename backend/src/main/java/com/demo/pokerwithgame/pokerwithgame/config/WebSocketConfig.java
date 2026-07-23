package com.demo.pokerwithgame.pokerwithgame.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 프론트엔드에서 접속할 엔드포인트: ws://localhost:8080/ws
        // 개발 단계이므로 CORS를 모두 열어둡니다.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 서버 -> 클라이언트로 메시지를 보낼 때 사용하는 prefix
        registry.enableSimpleBroker("/topic");
        // 클라이언트 -> 서버로 메시지를 보낼 때 사용하는 prefix
        registry.setApplicationDestinationPrefixes("/app");
    }
}