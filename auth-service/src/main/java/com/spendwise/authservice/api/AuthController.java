package com.spendwise.authservice.api;

import com.spendwise.authservice.service.AuthService;
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
 * Register/lookup only at this milestone. Login, JWT issuance, and refresh-token
 * rotation require the OAuth2 Authorization Server wiring introduced in Milestone 6.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<CredentialResponse> register(@RequestBody RegisterRequest request) {
        CredentialResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CredentialResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(authService.getById(id));
    }
}
