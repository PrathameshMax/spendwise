package com.prathmesh.spendwise.userservice.service.impl;

import com.prathmesh.spendwise.userservice.dto.request.UserRequest;
import com.prathmesh.spendwise.userservice.dto.response.UserResponse;
import com.prathmesh.spendwise.userservice.entity.User;
import com.prathmesh.spendwise.userservice.service.UserService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    private final Map<Long, User> users = new HashMap<>();
    private Long idCounter = 1L;

    @Override
    public UserResponse createUser(UserRequest request) {

        User user = new User();

        user.setId(idCounter++);
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());

        users.put(user.getId(), user);

        return mapToResponse(user);
    }

    @Override
    public List<UserResponse> getAllUsers() {

        List<UserResponse> responseList = new ArrayList<>();

        for (User user : users.values()) {
            responseList.add(mapToResponse(user));
        }

        return responseList;
    }

    @Override
    public UserResponse getUserById(Long id) {

        User user = users.get(id);

        if (user == null) {
            throw new RuntimeException("User not found");
        }

        return mapToResponse(user);
    }

    @Override
    public UserResponse updateUser(Long id, UserRequest request) {

        User user = users.get(id);

        if (user == null) {
            throw new RuntimeException("User not found");
        }

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());

        return mapToResponse(user);
    }

    @Override
    public void deleteUser(Long id) {

        if (!users.containsKey(id)) {
            throw new RuntimeException("User not found");
        }

        users.remove(id);
    }

    private UserResponse mapToResponse(User user) {

        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail()
        );
    }
}
