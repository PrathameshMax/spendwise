package com.spendwise.authservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    boolean existsByEmail(String email);

    Optional<Credential> findByEmail(String email);
}
