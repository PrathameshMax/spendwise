package com.spendwise.userservice.service;

import com.spendwise.common.exception.DuplicateResourceException;
import com.spendwise.common.exception.ResourceNotFoundException;
import com.spendwise.userservice.api.UserRequest;
import com.spendwise.userservice.api.UserResponse;
import com.spendwise.userservice.domain.UserProfile;
import com.spendwise.userservice.domain.UserProfileRepository;
import com.spendwise.userservice.mapper.UserProfileMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper userProfileMapper;

    public UserService(UserProfileRepository userProfileRepository, UserProfileMapper userProfileMapper) {
        this.userProfileRepository = userProfileRepository;
        this.userProfileMapper = userProfileMapper;
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        if (userProfileRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("UserProfile", "email", request.email());
        }
        UserProfile saved = userProfileRepository.save(
                new UserProfile(request.email(), request.fullName(), request.preferredCurrency()));
        return userProfileMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return userProfileMapper.toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        return userProfileRepository.findAll(pageable).map(userProfileMapper::toResponse);
    }

    @Transactional
    public UserResponse update(UUID id, UserRequest request) {
        UserProfile userProfile = findOrThrow(id);
        userProfile.updateProfile(request.fullName(), request.preferredCurrency());
        return userProfileMapper.toResponse(userProfile);
    }

    private UserProfile findOrThrow(UUID id) {
        return userProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserProfile", id));
    }
}
