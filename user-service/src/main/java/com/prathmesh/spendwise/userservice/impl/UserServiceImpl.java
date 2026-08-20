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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

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

        User user = userMapper.toEntity(request);

        try {
            User savedUser = userRepository.saveAndFlush(user);

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
    public UserResponse getUserById(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with Id : " + id
                        ));

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