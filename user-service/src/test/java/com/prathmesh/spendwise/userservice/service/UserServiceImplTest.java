package com.prathmesh.spendwise.userservice.service;

import com.prathmesh.spendwise.userservice.dto.request.UserRequest;
import com.prathmesh.spendwise.userservice.dto.response.UserResponse;
import com.prathmesh.spendwise.userservice.entity.User;
import com.prathmesh.spendwise.userservice.exception.DuplicateEmailException;
import com.prathmesh.spendwise.userservice.exception.InvalidSortFieldException;
import com.prathmesh.spendwise.userservice.exception.ResourceNotFoundException;
import com.prathmesh.spendwise.userservice.mapper.UserMapper;
import com.prathmesh.spendwise.userservice.repository.UserRepository;
import com.prathmesh.spendwise.userservice.impl.UserServiceImpl;
import com.prathmesh.spendwise.userservice.validation.UserSortValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserSortValidator userSortValidator;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;
    private UserRequest userRequest;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {

        userRequest = new UserRequest();
        userRequest.setFirstName("Prathamesh");
        userRequest.setLastName("Padavekar");
        userRequest.setEmail("prathamesh@gmail.com");
        userRequest.setPhone("9876543210");

        user = new User();
        user.setId(1L);
        user.setFirstName("Prathamesh");
        user.setLastName("Padavekar");
        user.setEmail("prathamesh@gmail.com");
        user.setPhone("9876543210");

        userResponse = new UserResponse();
        userResponse.setId(1L);
        userResponse.setFirstName("Prathamesh");
        userResponse.setLastName("Padavekar");
        userResponse.setEmail("prathamesh@gmail.com");
        userResponse.setPhone("9876543210");
    }


    // --------------------------------------------------
    // CREATE
    // --------------------------------------------------

    @Test
    void createUser_shouldCreateUserSuccessfully() {

        when(userMapper.toEntity(userRequest))
                .thenReturn(user);

        when(userRepository.saveAndFlush(user))
                .thenReturn(user);

        when(userMapper.toResponse(user))
                .thenReturn(userResponse);

        UserResponse result =
                userService.createUser(userRequest);

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

        verify(userMapper).toEntity(userRequest);
        verify(userRepository).saveAndFlush(user);
        verify(userMapper).toResponse(user);
    }


    // --------------------------------------------------
    // CREATE - DUPLICATE EMAIL
    // --------------------------------------------------

    @Test
    void createUser_shouldThrowDuplicateEmailException() {

        UserRequest request = new UserRequest();
        request.setFirstName("Prathamesh");
        request.setLastName("Padavekar");
        request.setEmail("duplicate@gmail.com");
        request.setPhone("9876543210");

        User duplicateUser = new User();

        when(userMapper.toEntity(request))
                .thenReturn(duplicateUser);

        when(userRepository.saveAndFlush(duplicateUser))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "duplicate key value violates unique constraint"
                        )
                );

        DuplicateEmailException exception =
                assertThrows(
                        DuplicateEmailException.class,
                        () -> userService.createUser(request)
                );

        assertEquals(
                "User with email 'duplicate@gmail.com' already exists",
                exception.getMessage()
        );

        verify(userMapper).toEntity(request);
        verify(userRepository).saveAndFlush(duplicateUser);
        verify(userMapper, never())
                .toResponse(any(User.class));
    }


    // --------------------------------------------------
    // GET ALL
    // --------------------------------------------------

    @Test
    void getAllUsers_shouldReturnAllUsers() {

        User user2 = new User();
        user2.setId(2L);
        user2.setFirstName("Dipti");
        user2.setLastName("Malave");
        user2.setEmail("dipti@gmail.com");
        user2.setPhone("9234567890");

        UserResponse response2 = new UserResponse();
        response2.setId(2L);
        response2.setFirstName("Dipti");
        response2.setLastName("Malave");
        response2.setEmail("dipti@gmail.com");
        response2.setPhone("9234567890");

        Page<User> userPage =
                new PageImpl<>(List.of(user, user2));

        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(userPage);

        when(userMapper.toResponse(user))
                .thenReturn(userResponse);

        when(userMapper.toResponse(user2))
                .thenReturn(response2);

        Pageable pageable = Pageable.ofSize(2);

        Page<UserResponse> result =
                userService.getAllUsers(pageable);

        assertNotNull(result);
        assertEquals(2, result.getContent().size());

        assertEquals(
                "Prathamesh",
                result.getContent().get(0).getFirstName()
        );

        assertEquals(
                "Dipti",
                result.getContent().get(1).getFirstName()
        );

        verify(userSortValidator)
                .validate(pageable.getSort());

        verify(userRepository)
                .findAll(pageable);

        verify(userMapper).toResponse(user);
        verify(userMapper).toResponse(user2);
    }


    // --------------------------------------------------
    // GET BY ID - SUCCESS
    // --------------------------------------------------

    @Test
    void getUserById_shouldReturnUserSuccessfully() {

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(userMapper.toResponse(user))
                .thenReturn(userResponse);

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

        verify(userRepository).findById(1L);
        verify(userMapper).toResponse(user);
    }


    // --------------------------------------------------
    // GET BY ID - NOT FOUND
    // --------------------------------------------------

    @Test
    void getUserById_shouldThrowExceptionWhenUserNotFound() {

        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> userService.getUserById(99L)
                );

        assertNotNull(exception);

        verify(userRepository).findById(99L);

        verify(userMapper, never())
                .toResponse(any(User.class));
    }


    // --------------------------------------------------
    // UPDATE - SUCCESS
    // --------------------------------------------------

    @Test
    void updateUser_shouldUpdateUserSuccessfully() {

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(userRepository.save(user))
                .thenReturn(user);

        when(userMapper.toResponse(user))
                .thenReturn(userResponse);

        UserResponse result =
                userService.updateUser(
                        1L,
                        userRequest
                );

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

        verify(userRepository).findById(1L);
        verify(userRepository).save(user);
        verify(userMapper).toResponse(user);
    }


    // --------------------------------------------------
    // UPDATE - NOT FOUND
    // --------------------------------------------------

    @Test
    void updateUser_shouldThrowExceptionWhenUserNotFound() {

        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> userService.updateUser(
                        99L,
                        userRequest
                )
        );

        verify(userRepository).findById(99L);

        verify(userRepository, never())
                .save(any(User.class));
    }


    // --------------------------------------------------
    // DELETE - SUCCESS
    // --------------------------------------------------

    @Test
    void deleteUser_shouldDeleteUserSuccessfully() {

        when(userRepository.existsById(1L))
                .thenReturn(true);

        doNothing()
                .when(userRepository)
                .deleteById(1L);

        userService.deleteUser(1L);

        verify(userRepository).existsById(1L);
        verify(userRepository).deleteById(1L);
    }


    // --------------------------------------------------
    // DELETE - NOT FOUND
    // --------------------------------------------------

    @Test
    void deleteUser_shouldThrowExceptionWhenUserNotFound() {

        when(userRepository.existsById(99L))
                .thenReturn(false);

        assertThrows(
                ResourceNotFoundException.class,
                () -> userService.deleteUser(99L)
        );

        verify(userRepository).existsById(99L);

        verify(userRepository, never())
                .deleteById(99L);
    }


    // --------------------------------------------------
    // SORTING VALIDATION
    // --------------------------------------------------

    @Test
    void getAllUsers_shouldRejectInvalidSortField() {

        Pageable pageable = PageRequest.of(
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

        assertThrows(
                InvalidSortFieldException.class,
                () -> userService.getAllUsers(pageable)
        );

        verify(userSortValidator)
                .validate(pageable.getSort());

        verify(userRepository, never())
                .findAll(any(Pageable.class));
    }


    // --------------------------------------------------
    // SEARCH
    // --------------------------------------------------

    @Test
    void searchUsers_shouldPassSearchAndPageableToRepository() {

        Pageable pageable = PageRequest.of(
                0,
                2,
                Sort.by("createdAt").descending()
        );

        Page<User> userPage =
                new PageImpl<>(List.of(user));

        when(userRepository.searchUsers(
                "gmail",
                pageable
        ))
                .thenReturn(userPage);

        when(userMapper.toResponse(user))
                .thenReturn(userResponse);

        Page<UserResponse> result =
                userService.searchUsers(
                        "gmail",
                        pageable
                );

        assertNotNull(result);
        assertEquals(
                1,
                result.getContent().size()
        );

        verify(userSortValidator)
                .validate(pageable.getSort());

        verify(userRepository)
                .searchUsers(
                        "gmail",
                        pageable
                );

        verify(userMapper)
                .toResponse(user);
    }
}