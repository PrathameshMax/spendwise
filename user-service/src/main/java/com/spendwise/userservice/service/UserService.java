package com.spendwise.userservice.service;

import com.spendwise.common.exception.DuplicateResourceException;
import com.spendwise.common.exception.ResourceNotFoundException;
import com.spendwise.userservice.api.CreateUserRequest;
import com.spendwise.userservice.api.UpdateUserRequest;
import com.spendwise.userservice.api.UserResponse;
import com.spendwise.userservice.domain.UserProfile;
import com.spendwise.userservice.domain.UserProfileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserProfileRepository userProfileRepository;

    public UserService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userProfileRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("UserProfile", "email", request.email());
        }
        UserProfile saved = userProfileRepository.save(
                new UserProfile(request.email(), request.fullName(), request.preferredCurrency()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        return userProfileRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        UserProfile userProfile = findOrThrow(id);
        userProfile.updateProfile(request.fullName(), request.preferredCurrency());
        return toResponse(userProfile);
    }

    private UserProfile findOrThrow(UUID id) {
        return userProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserProfile", id));
    }

    private UserResponse toResponse(UserProfile userProfile) {
        return new UserResponse(
                userProfile.getId(),
                userProfile.getEmail(),
                userProfile.getFullName(),
                userProfile.getPreferredCurrency(),
                userProfile.getCreatedAt(),
                userProfile.getUpdatedAt());
    }
}
