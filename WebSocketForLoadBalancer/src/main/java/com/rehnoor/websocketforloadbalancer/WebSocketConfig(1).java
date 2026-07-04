package com.rehnoor.websocketforloadbalancer;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LoadBalancerWebSocketHandler loadBalancerWebSocketHandler;

    public WebSocketConfig(LoadBalancerWebSocketHandler loadBalancerWebSocketHandler) {
        this.loadBalancerWebSocketHandler = loadBalancerWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(loadBalancerWebSocketHandler, "/lb-server-channel")
                .setAllowedOriginPatterns("*");
    }
}
