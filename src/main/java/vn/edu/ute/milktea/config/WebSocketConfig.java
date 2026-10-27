package vn.edu.ute.milktea.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import vn.edu.ute.milktea.entity.account.Account;
import vn.edu.ute.milktea.entity.account.Role;
import vn.edu.ute.milktea.repository.account.AccountRepository;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.security.GuestAccessService;
import vn.edu.ute.milktea.security.JwtService;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final AccountRepository accountRepository;
    private final GuestAccessService guestAccessService;

    @org.springframework.beans.factory.annotation.Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;
    @org.springframework.beans.factory.annotation.Value("${app.websocket.allowed-origin:http://localhost:8080}")
    private String allowedOrigin;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        org.springframework.web.socket.server.HandshakeInterceptor cookieInterceptor = new org.springframework.web.socket.server.HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(org.springframework.http.server.ServerHttpRequest request,
                                          org.springframework.http.server.ServerHttpResponse response,
                                          org.springframework.web.socket.WebSocketHandler wsHandler,
                                          Map<String, Object> attributes) {
                if (request instanceof org.springframework.http.server.ServletServerHttpRequest servletRequest) {
                    jakarta.servlet.http.HttpServletRequest httpRequest = servletRequest.getServletRequest();
                    jakarta.servlet.http.Cookie[] cookies = httpRequest.getCookies();
                    if (cookies != null) {
                        for (jakarta.servlet.http.Cookie c : cookies) {
                            if (vn.edu.ute.milktea.security.JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE.equals(c.getName())) {
                                attributes.put(vn.edu.ute.milktea.security.JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE, c.getValue());
                            }
                        }
                    }
                }
                return true;
            }

            @Override
            public void afterHandshake(org.springframework.http.server.ServerHttpRequest request,
                                       org.springframework.http.server.ServerHttpResponse response,
                                       org.springframework.web.socket.WebSocketHandler wsHandler,
                                       Exception exception) {
            }
        };

        // Do not expose authenticated staff topics to arbitrary cross-origin pages.
        java.util.Set<String> origins = new java.util.LinkedHashSet<>();
        if (baseUrl != null && !baseUrl.isBlank()) origins.add(baseUrl.trim());
        if (allowedOrigin != null && !allowedOrigin.isBlank()) origins.add(allowedOrigin.trim());
        origins.add("http://localhost:8080");
        origins.add("http://127.0.0.1:8080");
        String[] originPatterns = origins.toArray(String[]::new);
        registry.addEndpoint("/ws")
                .addInterceptors(cookieInterceptor)
                .setAllowedOriginPatterns(originPatterns);
        registry.addEndpoint("/ws")
                .addInterceptors(cookieInterceptor)
                .setAllowedOriginPatterns(originPatterns)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticateStompConnect(accessor);
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    authorizeStompSubscribe(accessor);
                } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                    // Cấm SEND command nghiệp vụ trực tiếp từ client theo realtime_rule.md
                    log.warn("Client cố gắng gửi STOMP SEND trực tiếp tới: {}", accessor.getDestination());
                    return null;
                }

                return message;
            }
        });
    }

    private void authenticateStompConnect(StompHeaderAccessor accessor) {
        // 1. Kiểm tra header Authorization / Cookie JWT
        String token = null;
        String bearer = accessor.getFirstNativeHeader("Authorization");
        if (bearer == null) {
            bearer = accessor.getFirstNativeHeader("authorization");
        }
        if (bearer != null && bearer.startsWith("Bearer ")) {
            token = bearer.substring(7).trim();
        }

        // 2. Nếu không có header, lấy từ session attributes (được trích xuất từ cookie ở HTTP handshake)
        if (token == null && accessor.getSessionAttributes() != null) {
            Object cookieToken = accessor.getSessionAttributes().get(vn.edu.ute.milktea.security.JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE);
            if (cookieToken instanceof String ct && !ct.isBlank()) {
                token = ct.trim();
            }
        }

        if (token != null) {
            Map<String, Object> claims = jwtService.validateAndExtractClaims(token);
            if (claims != null) {
                Long accountId = Long.parseLong((String) claims.get("sub"));
                Long claimVersion = ((Number) claims.get("tokenVersion")).longValue();

                Optional<Account> accountOpt = accountRepository.findById(accountId);
                if (accountOpt.isPresent()) {
                    Account account = accountOpt.get();
                    if (Boolean.TRUE.equals(account.getActive()) && account.getTokenVersion().equals(claimVersion)) {
                        Role role = account.getRole();
                        CurrentActor actor = CurrentActor.builder()
                                .actorType(CurrentActor.ActorType.AUTHENTICATED)
                                .accountId(account.getId())
                                .email(account.getEmail())
                                .role(role)
                                .build();

                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                actor, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name())));
                        accessor.setUser(auth);
                        return;
                    }
                }
            }
        }

        // 2. Kiểm tra Table Session Token cho khách bàn
        String tableToken = accessor.getFirstNativeHeader("X-Table-Session-Token");
        if (tableToken == null) {
            tableToken = accessor.getFirstNativeHeader("X-Table-Token");
        }
        if (tableToken != null && !tableToken.isBlank()) {
            var accessOpt = guestAccessService.validateTableToken(tableToken);
            if (accessOpt.isPresent()) {
                var access = accessOpt.get();
                CurrentActor actor = CurrentActor.builder()
                        .actorType(CurrentActor.ActorType.TABLE_GUEST)
                        .sessionId(access.getSession().getId())
                        .tableId(access.getSession().getTable().getId())
                        .build();

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        actor, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_GUEST")));
                accessor.setUser(auth);
                return;
            }
        }

        // 3. Kiểm tra Counter Order Token cho khách quầy
        String counterToken = accessor.getFirstNativeHeader("X-Counter-Order-Token");
        if (counterToken == null) {
            counterToken = accessor.getFirstNativeHeader("X-Counter-Token");
        }
        if (counterToken != null && !counterToken.isBlank()) {
            var accessOpt = guestAccessService.validateCounterToken(counterToken);
            if (accessOpt.isPresent()) {
                var access = accessOpt.get();
                CurrentActor actor = CurrentActor.builder()
                        .actorType(CurrentActor.ActorType.COUNTER_GUEST)
                        .orderId(access.getOrder().getId())
                        .build();

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        actor, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_GUEST")));
                accessor.setUser(auth);
                return;
            }
        }

        log.debug("STOMP CONNECT từ anonymous guest hoặc public client");
    }

    private void authorizeStompSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        Object principalObj = accessor.getUser();
        CurrentActor actor = null;
        if (principalObj instanceof UsernamePasswordAuthenticationToken token) {
            if (token.getPrincipal() instanceof CurrentActor ca) {
                actor = ca;
            }
        }

        if (!isAllowedSubscription(destination, actor)) {
            log.warn("Từ chối STOMP SUBSCRIBE destination={}", destination);
            throw new IllegalArgumentException("Không có quyền truy cập kênh realtime");
        }
    }

    /** Allow only published application channels, never arbitrary broker destinations. */
    static boolean isAllowedSubscription(String destination, CurrentActor actor) {
        if (destination == null || actor == null) return false;
        Role role = actor.getRole();
        switch (destination) {
            case "/topic/cashier": return role == Role.CASHIER || role == Role.ADMIN;
            case "/topic/kitchen", "/topic/stock": return role == Role.KITCHEN || role == Role.ADMIN;
            case "/topic/admin": return role == Role.ADMIN;
            default: {
                final String prefix = "/topic/table-sessions/";
                if (!destination.startsWith(prefix)) return false;
                String id = destination.substring(prefix.length());
                // Reject paths, suffixes, malformed IDs and zero/negative session IDs.
                if (!id.matches("[1-9][0-9]{0,18}")) return false;
                try {
                    long session = Long.parseLong(id);
                    return role == Role.ADMIN || role == Role.CASHIER ||
                           (actor.getSessionId() != null && actor.getSessionId().equals(session));
                } catch (NumberFormatException exception) {
                    return false;
                }
            }
        }
    }
}
