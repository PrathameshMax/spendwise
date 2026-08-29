package com.prathmesh.spendwise.userservice.service;

import com.prathmesh.spendwise.userservice.dto.request.UserRequest;
import com.prathmesh.spendwise.userservice.dto.response.UserResponse;
import com.prathmesh.spendwise.userservice.entity.User;
import com.prathmesh.spendwise.userservice.exception.DuplicateEmailException;
import com.prathmesh.spendwise.userservice.exception.InvalidSortFieldException;
import com.prathmesh.spendwise.userservice.exception.ResourceNotFoundException;
import com.prathmesh.spendwise.userservice.impl.UserServiceImpl;
import com.prathmesh.spendwise.userservice.mapper.UserMapper;
import com.prathmesh.spendwise.userservice.repository.UserRepository;
import com.prathmesh.spendwise.userservice.validation.UserSortValidator;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.Spy;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserSortValidator userSortValidator;

    @InjectMocks
    private UserServiceImpl userService;

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private User user;
    private UserRequest request;
    private UserResponse response;

    @BeforeEach
    void setUp() {

        request = new UserRequest();
        request.setFirstName("Prathamesh");
        request.setLastName("Padavekar");
        request.setEmail("prathamesh@gmail.com");
        request.setPhone("9876543210");

        user = new User();
        user.setId(1L);
        user.setFirstName("Prathamesh");
        user.setLastName("Padavekar");
        user.setEmail("prathamesh@gmail.com");
        user.setPhone("9876543210");

        response = new UserResponse();
        response.setId(1L);
        response.setFirstName("Prathamesh");
        response.setLastName("Padavekar");
        response.setEmail("prathamesh@gmail.com");
        response.setPhone("9876543210");
    }


    // =========================================================
    // CREATE
    // =========================================================

    @Test
    void createUser_shouldCreateUserSuccessfully() {

        when(userMapper.toEntity(request))
                .thenReturn(user);

        when(userRepository.saveAndFlush(user))
                .thenReturn(user);

        when(userMapper.toResponse(user))
                .thenReturn(response);

        UserResponse result =
                userService.createUser(request);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(
                "Prathamesh",
                result.getFirstName()
        );
        assertEquals(
                "prathamesh@gmail.com",
                result.getEmail()
        );

        verify(userMapper)
                .toEntity(request);

        verify(userRepository)
                .saveAndFlush(user);

        verify(userMapper)
                .toResponse(user);

        assertEquals(
                1.0,
                meterRegistry
                        .counter("spendwise.users.created")
                        .count()
        );
    }


    @Test
    void createUser_shouldThrowDuplicateEmailException() {

        when(userMapper.toEntity(request))
                .thenReturn(user);

        when(userRepository.saveAndFlush(user))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "Duplicate email"
                        )
                );

        DuplicateEmailException exception =
                assertThrows(
                        DuplicateEmailException.class,
                        () -> userService.createUser(request)
                );

        assertEquals(
                "User with email 'prathamesh@gmail.com' already exists",
                exception.getMessage()
        );

        verify(userMapper)
                .toEntity(request);

        verify(userRepository)
                .saveAndFlush(user);

        verify(userMapper, never())
                .toResponse(any(User.class));
    }


    // =========================================================
    // GET ALL
    // =========================================================

    @Test
    void getAllUsers_shouldReturnUsers() {

        Pageable pageable =
                PageRequest.of(
                        0,
                        10,
                        Sort.by("createdAt").descending()
                );

        Page<User> userPage =
                new PageImpl<>(
                        List.of(user),
                        pageable,
                        1
                );

        when(userRepository.findAll(pageable))
                .thenReturn(userPage);

        when(userMapper.toResponse(user))
                .thenReturn(response);

        Page<UserResponse> result =
                userService.getAllUsers(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());

        assertEquals(
                "Prathamesh",
                result.getContent()
                        .get(0)
                        .getFirstName()
        );

        verify(userSortValidator)
                .validate(pageable.getSort());

        verify(userRepository)
                .findAll(pageable);

        verify(userMapper)
                .toResponse(user);
    }


    @Test
    void getAllUsers_shouldReturnEmptyPage() {

        Pageable pageable =
                PageRequest.of(0, 10);

        Page<User> emptyPage =
                new PageImpl<>(List.of());

        when(userRepository.findAll(pageable))
                .thenReturn(emptyPage);

        Page<UserResponse> result =
                userService.getAllUsers(pageable);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(userSortValidator)
                .validate(pageable.getSort());

        verify(userRepository)
                .findAll(pageable);

        verify(userMapper, never())
                .toResponse(any(User.class));
    }


    @Test
    void getAllUsers_shouldRejectInvalidSortField() {

        Pageable pageable =
                PageRequest.of(
                        0,
                        10,
                        Sort.by("password").ascending()
                );

        doThrow(
                new InvalidSortFieldException(
                        "Sorting by field 'password' is not allowed"
                )
        )
                .when(userSortValidator)
                .validate(pageable.getSort());

        InvalidSortFieldException exception =
                assertThrows(
                        InvalidSortFieldException.class,
                        () -> userService.getAllUsers(pageable)
                );

        assertEquals(
                "Sorting by field 'password' is not allowed",
                exception.getMessage()
        );

        verify(userSortValidator)
                .validate(pageable.getSort());

        verify(userRepository, never())
                .findAll(any(Pageable.class));
    }


    // =========================================================
    // SEARCH
    // =========================================================

    @Test
    void searchUsers_shouldReturnMatchingUsers() {

        Pageable pageable =
                PageRequest.of(
                        0,
                        10,
                        Sort.by("createdAt").descending()
                );

        Page<User> userPage =
                new PageImpl<>(
                        List.of(user),
                        pageable,
                        1
                );

        when(userRepository.searchUsers(
                "gmail",
                pageable
        )).thenReturn(userPage);

        when(userMapper.toResponse(user))
                .thenReturn(response);

        Page<UserResponse> result =
                userService.searchUsers(
                        "gmail",
                        pageable
                );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(
                "prathamesh@gmail.com",
                result.getContent()
                        .get(0)
                        .getEmail()
        );

        verify(userSortValidator)
                .validate(pageable.getSort());

        verify(userRepository)
                .searchUsers("gmail", pageable);

        verify(userMapper)
                .toResponse(user);
    }


    @Test
    void searchUsers_shouldReturnEmptyPage() {

        Pageable pageable =
                PageRequest.of(0, 10);

        Page<User> emptyPage =
                new PageImpl<>(List.of());

        when(userRepository.searchUsers(
                "xyz",
                pageable
        )).thenReturn(emptyPage);

        Page<UserResponse> result =
                userService.searchUsers(
                        "xyz",
                        pageable
                );

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(userSortValidator)
                .validate(pageable.getSort());

        verify(userRepository)
                .searchUsers("xyz", pageable);
    }


    @Test
    void searchUsers_shouldRejectInvalidSortField() {

        Pageable pageable =
                PageRequest.of(
                        0,
                        10,
                        Sort.by("password").descending()
                );

        doThrow(
                new InvalidSortFieldException(
                        "Sorting by field 'password' is not allowed"
                )
        )
                .when(userSortValidator)
                .validate(pageable.getSort());

        assertThrows(
                InvalidSortFieldException.class,
                () -> userService.searchUsers(
                        "gmail",
                        pageable
                )
        );

        verify(userSortValidator)
                .validate(pageable.getSort());

        verify(userRepository, never())
                .searchUsers(
                        anyString(),
                        any(Pageable.class)
                );
    }


    // =========================================================
    // GET BY ID
    // =========================================================

    @Test
    void getUserById_shouldReturnUser() {

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(userMapper.toResponse(user))
                .thenReturn(response);

        UserResponse result =
                userService.getUserById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(
                "Prathamesh",
                result.getFirstName()
        );
        assertEquals(
                "prathamesh@gmail.com",
                result.getEmail()
        );

        verify(userRepository)
                .findById(1L);

        verify(userMapper)
                .toResponse(user);
    }


    @Test
    void getUserById_shouldThrowExceptionWhenUserNotFound() {

        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> userService.getUserById(99L)
                );

        assertEquals(
                "User not found with Id : 99",
                exception.getMessage()
        );

        verify(userRepository)
                .findById(99L);

        verify(userMapper, never())
                .toResponse(any(User.class));
    }


    // =========================================================
    // UPDATE
    // =========================================================

    @Test
    void updateUser_shouldUpdateUserSuccessfully() {

        User updatedUser = new User();

        updatedUser.setId(1L);
        updatedUser.setFirstName("Updated");
        updatedUser.setLastName("User");
        updatedUser.setEmail("updated@gmail.com");
        updatedUser.setPhone("9999999999");

        UserResponse updatedResponse =
                new UserResponse();

        updatedResponse.setId(1L);
        updatedResponse.setFirstName("Updated");
        updatedResponse.setLastName("User");
        updatedResponse.setEmail("updated@gmail.com");
        updatedResponse.setPhone("9999999999");

        request.setFirstName("Updated");
        request.setLastName("User");
        request.setEmail("updated@gmail.com");
        request.setPhone("9999999999");

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(userRepository.save(user))
                .thenReturn(updatedUser);

        when(userMapper.toResponse(updatedUser))
                .thenReturn(updatedResponse);

        UserResponse result =
                userService.updateUser(
                        1L,
                        request
                );

        assertNotNull(result);

        assertEquals(
                "Updated",
                result.getFirstName()
        );

        assertEquals(
                "updated@gmail.com",
                result.getEmail()
        );

        assertEquals(
                "9999999999",
                result.getPhone()
        );

        assertEquals(
                "Updated",
                user.getFirstName()
        );

        assertEquals(
                "User",
                user.getLastName()
        );

        assertEquals(
                "updated@gmail.com",
                user.getEmail()
        );

        assertEquals(
                "9999999999",
                user.getPhone()
        );

        verify(userRepository)
                .findById(1L);

        verify(userRepository)
                .save(user);

        verify(userMapper)
                .toResponse(updatedUser);

        assertEquals(
                1.0,
                meterRegistry
                        .counter("spendwise.users.updated")
                        .count()
        );
    }


    @Test
    void updateUser_shouldThrowExceptionWhenUserNotFound() {

        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> userService.updateUser(
                                99L,
                                request
                        )
                );

        assertEquals(
                "User not found with Id : 99",
                exception.getMessage()
        );

        verify(userRepository)
                .findById(99L);

        verify(userRepository, never())
                .save(any(User.class));
    }


    // =========================================================
    // DELETE
    // =========================================================

    @Test
    void deleteUser_shouldDeleteUserSuccessfully() {

        when(userRepository.existsById(1L))
                .thenReturn(true);

        userService.deleteUser(1L);

        verify(userRepository)
                .existsById(1L);

        verify(userRepository)
                .deleteById(1L);

        assertEquals(
                1.0,
                meterRegistry
                        .counter("spendwise.users.deleted")
                        .count()
        );
    }


    @Test
    void deleteUser_shouldThrowExceptionWhenUserNotFound() {

        when(userRepository.existsById(99L))
                .thenReturn(false);

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> userService.deleteUser(99L)
                );

        assertEquals(
                "User not found with Id : 99",
                exception.getMessage()
        );

        verify(userRepository)
                .existsById(99L);

        verify(userRepository, never())
                .deleteById(99L);
    }
}