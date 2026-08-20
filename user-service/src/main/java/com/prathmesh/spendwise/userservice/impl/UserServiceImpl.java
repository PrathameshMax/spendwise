package com.prathmesh.spendwise.userservice.impl;

import com.prathmesh.spendwise.userservice.dto.request.UserRequest;
import com.prathmesh.spendwise.userservice.dto.response.UserResponse;
import com.prathmesh.spendwise.userservice.entity.User;
import com.prathmesh.spendwise.userservice.exception.DuplicateEmailException;
import com.prathmesh.spendwise.userservice.exception.ResourceNotFoundException;
import com.prathmesh.spendwise.userservice.mapper.UserMapper;
import com.prathmesh.spendwise.userservice.repository.UserRepository;
import com.prathmesh.spendwise.userservice.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    private final Map<Long, User> users = new HashMap<>();
    private Long idCounter = 1L;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Override
    public UserResponse createUser(UserRequest request) {

        User user = userMapper.toEntity(request);

        try {
            User savedUser = userRepository.saveAndFlush(user);
            return userMapper.toResponse(savedUser);

        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateEmailException(
                    "User with email '" + request.getEmail() + "' already exists"
            );
        }
    }

    @Override
    public List<UserResponse> getAllUsers() {

        return userRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public UserResponse getUserById(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found with Id : "+id));

        return mapToResponse(user);
    }

    @Override
    public UserResponse updateUser(Long id, UserRequest request) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found with Id : "+id)
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

        if (!userRepository.existsById(id)){
            throw new ResourceNotFoundException("User not found with Id : "+id);
        }

        userRepository.deleteById(id);
    }

    private UserResponse mapToResponse(User user) {

        return userMapper.toResponse(user);
    }
}
