package com.rehnoor.websocketforloadbalancer;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class LoadBalancerWebSocketHandlerTest {

    @Test
    void buildConnectionErrorMessageIncludesBackendIpAndTelemetryHint() {
        LoadBalancerWebSocketHandler handler = new LoadBalancerWebSocketHandler(new ObjectMapper());

        String message = handler.buildConnectionErrorMessage(
                "192.168.1.50",
                "ws://192.168.1.50:8086/telemetry",
                new RuntimeException("Connection refused")
        );

        assertThat(message)
                .contains("192.168.1.50")
                .contains("telemetry connector")
                .contains("Libre Hardware Monitor");
    }
}
