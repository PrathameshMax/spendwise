package com.spendwise.authservice.api;

import com.spendwise.authservice.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * auth-service's public surface (Milestone 6): register and login both mint an
 * access/refresh token pair, and refresh rotates a still-valid refresh token for
 * a new pair. The API Gateway is what turns {@code refreshToken} into a secure,
 * HttpOnly cookie and strips it back out of the JSON body before it reaches a
 * browser client — this controller only knows about the token pair itself, not
 * the cookie transport (see api-gateway's RouteConfig / RefreshCookieSupport).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<TokenPairResponse> register(@Valid @RequestBody RegisterRequest request) {
        TokenPairResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenPairResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CredentialResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(authService.getById(id));
    }
}
