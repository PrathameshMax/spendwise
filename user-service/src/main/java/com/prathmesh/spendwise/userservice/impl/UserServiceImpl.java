package com.prathmesh.spendwise.userservice.impl;

import com.prathmesh.spendwise.userservice.UserService;
import com.prathmesh.spendwise.userservice.dto.request.UserRequest;
import com.prathmesh.spendwise.userservice.dto.response.UserResponse;
import com.prathmesh.spendwise.userservice.entity.User;
import com.prathmesh.spendwise.userservice.exception.DuplicateEmailException;
import com.prathmesh.spendwise.userservice.exception.ResourceNotFoundException;
import com.prathmesh.spendwise.userservice.mapper.UserMapper;
import com.prathmesh.spendwise.userservice.repository.UserRepository;
import com.prathmesh.spendwise.userservice.validation.UserSortValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserSortValidator userSortValidator;

    public UserServiceImpl(
            UserRepository userRepository,
            UserMapper userMapper,
            UserSortValidator userSortValidator) {

        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.userSortValidator = userSortValidator;
    }

    @Override
    public UserResponse createUser(UserRequest request) {

        log.info("Creating user, email={}", request.getEmail());

        User user = userMapper.toEntity(request);

        try {
            User savedUser = userRepository.saveAndFlush(user);
            log.info(
                    "User created successfully, userId={}",
                    savedUser.getId()
            );
            return mapToResponse(savedUser);

        } catch (DataIntegrityViolationException ex) {

            throw new DuplicateEmailException(
                    "User with email '" + request.getEmail()
                            + "' already exists"
            );
        }
    }

    @Override
    public Page<UserResponse> getAllUsers(Pageable pageable) {

        userSortValidator.validate(pageable.getSort());

        return userRepository.findAll(pageable)
                .map(userMapper::toResponse);
    }

    @Override
    public Page<UserResponse> searchUsers(
            String search,
            Pageable pageable) {

        userSortValidator.validate(pageable.getSort());

        return userRepository.searchUsers(search, pageable)
                .map(userMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {

        log.info("Fetching user, userId={}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("User not found, userId={}", id);

                    return new ResourceNotFoundException(
                            "User not found with Id : " + id
                    );
                });

        return mapToResponse(user);
    }

    @Override
    public UserResponse updateUser(
            Long id,
            UserRequest request) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with Id : " + id
                        )
                );

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());

        User updated = userRepository.save(user);

        return mapToResponse(updated);
    }

    @Override
    public void deleteUser(Long id) {

        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException(
                    "User not found with Id : " + id
            );
        }

        userRepository.deleteById(id);
    }

    private UserResponse mapToResponse(User user) {

        return userMapper.toResponse(user);
    }
}