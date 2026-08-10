package com.invault.inventory.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;

import com.invault.inventory.realtime.AuthenticatedWebSocketSessionRegistry;
import com.invault.inventory.realtime.JwtStompAuthenticationInterceptor;
import com.invault.inventory.realtime.RealtimeProperties;

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(RealtimeProperties.class)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final RealtimeProperties realtimeProperties;
    private final JwtStompAuthenticationInterceptor jwtAuthenticationInterceptor;
    private final AuthorizationManager<Message<?>> authorizationManager;
    private final ApplicationContext applicationContext;
    private final AuthenticatedWebSocketSessionRegistry sessionRegistry;

    public WebSocketConfig(
            RealtimeProperties realtimeProperties,
            JwtStompAuthenticationInterceptor jwtAuthenticationInterceptor,
            AuthorizationManager<Message<?>> websocketAuthorizationManager,
            ApplicationContext applicationContext,
            AuthenticatedWebSocketSessionRegistry sessionRegistry) {

        this.realtimeProperties = realtimeProperties;
        this.jwtAuthenticationInterceptor = jwtAuthenticationInterceptor;
        this.authorizationManager = websocketAuthorizationManager;
        this.applicationContext = applicationContext;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(new WebSocketHandlerDecoratorFactory() {
            @Override
            public WebSocketHandler decorate(WebSocketHandler handler) {
                return new WebSocketHandlerDecorator(handler) {
                    @Override
                    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                        sessionRegistry.registerTransport(session);
                        super.afterConnectionEstablished(session);
                    }

                    @Override
                    public void afterConnectionClosed(
                            WebSocketSession session,
                            CloseStatus closeStatus) throws Exception {
                        sessionRegistry.remove(session.getId());
                        super.afterConnectionClosed(session, closeStatus);
                    }
                };
            }
        });
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(realtimeProperties.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        AuthorizationChannelInterceptor authorizationInterceptor =
                new AuthorizationChannelInterceptor(authorizationManager);
        AuthorizationEventPublisher eventPublisher =
                new SpringAuthorizationEventPublisher(applicationContext);
        authorizationInterceptor.setAuthorizationEventPublisher(eventPublisher);

        registration.interceptors(
                jwtAuthenticationInterceptor,
                new SecurityContextChannelInterceptor(),
                authorizationInterceptor
        );
    }
}

/*
 * WebSocketConfig exposes native STOMP at /ws, uses the in-memory broker for the
 * MVP and authenticates JWT CONNECT frames before applying destination permissions.
 */
