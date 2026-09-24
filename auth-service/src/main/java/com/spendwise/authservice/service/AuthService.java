package com.spendwise.authservice.service;

import com.spendwise.authservice.api.CredentialResponse;
import com.spendwise.authservice.api.RegisterRequest;
import com.spendwise.authservice.domain.Credential;
import com.spendwise.authservice.domain.CredentialRepository;
import com.spendwise.common.exception.DuplicateResourceException;
import com.spendwise.common.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final CredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(CredentialRepository credentialRepository, PasswordEncoder passwordEncoder) {
        this.credentialRepository = credentialRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public CredentialResponse register(RegisterRequest request) {
        if (credentialRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Credential", "email", request.email());
        }
        Credential credential = new Credential(request.email(), passwordEncoder.encode(request.rawPassword()));
        Credential saved = credentialRepository.save(credential);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CredentialResponse getById(UUID id) {
        Credential credential = credentialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Credential", id));
        return toResponse(credential);
    }

    private CredentialResponse toResponse(Credential credential) {
        return new CredentialResponse(credential.getId(), credential.getEmail(), credential.getCreatedAt());
    }
}
