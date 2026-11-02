package com.metro.apigateway.filter;

import com.metro.apigateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class JwtGatewayFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private GatewayFilterChain chain;

    private JwtGatewayFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtGatewayFilter(jwtUtil);
        lenient().when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    void webhookPathPassesWithoutToken() {
        ServerWebExchange exchange = exchange(HttpMethod.POST, "/payments/webhook", null);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void actuatorHealthPassesWithoutToken() {
        ServerWebExchange exchange = exchange(HttpMethod.GET, "/actuator/health", null);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void otherActuatorEndpointStillNeedsToken() {
        ServerWebExchange exchange = exchange(HttpMethod.GET, "/actuator/env", null);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    void userCannotCallAdminBookings() {
        ServerWebExchange exchange = exchange(HttpMethod.GET, "/bookings/admin", "user-token");
        stubToken("user-token", "USER");

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    void adminCanCallAdminBookings() {
        ServerWebExchange exchange = exchange(HttpMethod.GET, "/bookings/admin", "admin-token");
        stubToken("admin-token", "ADMIN");

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void userCannotUseTicketEndpoint() {
        ServerWebExchange exchange = exchange(HttpMethod.GET, "/bookings/ticket/ABC123", "user-token");
        stubToken("user-token", "USER");

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    void userCannotCallAdminPaymentPost() {
        ServerWebExchange exchange = exchange(HttpMethod.POST, "/payments/admin/booking/1/refund", "user-token");
        stubToken("user-token", "USER");

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    void userCanReadOwnBooking() {
        ServerWebExchange exchange = exchange(HttpMethod.GET, "/bookings/5", "user-token");
        when(jwtUtil.isTokenValid("user-token")).thenReturn(true);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void userCannotWriteStations() {
        ServerWebExchange exchange = exchange(HttpMethod.POST, "/stations", "user-token");
        stubToken("user-token", "USER");

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    private void stubToken(String token, String role) {
        when(jwtUtil.isTokenValid(token)).thenReturn(true);
        when(jwtUtil.extractClaims(token)).thenReturn(claimsWithRole(role));
    }

    private Claims claimsWithRole(String role) {
        return Jwts.claims().add("role", role).build();
    }

    private ServerWebExchange exchange(HttpMethod method, String path, String token) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(method, path);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return MockServerWebExchange.from(builder.build());
    }
}
