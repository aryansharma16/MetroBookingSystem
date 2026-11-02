package com.metro.apigateway.filter;

import com.metro.apigateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    private static final List<String> PUBLIC_PATHS = List.of(
            "/auth/register",
            "/auth/login",
            "/auth/verify",
            "/auth/resend-verification",   // must be reachable without a token
            "/auth/forgot-password",
            "/auth/reset-password",
            "/payments/webhook",          // Razorpay calls this, it has no JWT
            "/actuator/health",           // load balancer health checks
            "/oauth2",
            "/login/oauth2",
            // Swagger on the gateway (dev only, remove before deploying)
            "/swagger-ui",
            "/webjars",
            "/v3/api-docs",
            "/auth-service/v3/api-docs",
            "/station-service/v3/api-docs",
            "/booking-service/v3/api-docs" ,  // NEW
            "/payment-service/v3/api-docs"
    );

    private static final List<String> ADMIN_ONLY_PATHS = List.of(
            "/stations",
            "/lines",
            "/segments",
            "/fare-slabs"
    );

    private static final List<String> ADMIN_PATHS = List.of(
            "/auth/admin",
            "/bookings/admin",
            "/payments/admin",
            "/bookings/ticket"
    );

    public JwtGatewayFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return rejectRequest(exchange, HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.isTokenValid(token)) {
            return rejectRequest(exchange, HttpStatus.UNAUTHORIZED);
        }

        if (isAdminOnlyWrite(path, request.getMethod()) || isAdminPath(path)) {
            Claims claims = jwtUtil.extractClaims(token);
            String role = claims.get("role", String.class);

            if (!"ADMIN".equals(role)) {
                return rejectRequest(exchange, HttpStatus.FORBIDDEN);
            }
        }

        return chain.filter(exchange);
    }

    private boolean isPublicPath(String path) {
        for (String publicPath : PUBLIC_PATHS) {
            if (path.startsWith(publicPath)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAdminOnlyWrite(String path, HttpMethod method) {
        // PATCH included, because CORS allows it and it would skip the admin check
        boolean isWriteMethod = HttpMethod.POST.equals(method)
                || HttpMethod.PUT.equals(method)
                || HttpMethod.PATCH.equals(method)
                || HttpMethod.DELETE.equals(method);

        if (!isWriteMethod) {
            return false;
        }

        for (String adminPath : ADMIN_ONLY_PATHS) {
            if (path.startsWith(adminPath)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAdminPath(String path) {
        for (String adminPath : ADMIN_PATHS) {
            if (path.startsWith(adminPath)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> rejectRequest(ServerWebExchange exchange, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}