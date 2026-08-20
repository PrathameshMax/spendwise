package com.prathmesh.spendwise.userservice.controller;

import com.prathmesh.spendwise.userservice.dto.request.UserRequest;
import com.prathmesh.spendwise.userservice.dto.response.UserResponse;
import com.prathmesh.spendwise.userservice.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.prathmesh.spendwise.userservice.exception.*;


@RestController
@RequestMapping("/api/v1/users")
@Tag(
        name = "User API",
        description = "APIs for managing Users"
)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(
            summary = "Create a new User",
            description = "Creates a new User and returns the created user details."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "User created successfully",
                    content = @Content(
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "User with the given email already exists",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody UserRequest request) {
        return userService.createUser(request);
    }

    @Operation(
            summary = "Get all users",
            description = "Returns all registered Users"
    )
    @ApiResponse(
            responseCode = "200",
            description = "Users retrieved successfully",
            content = @Content(
                    schema = @Schema(
                            implementation = UserResponse.class
                    )
            )
    )
    @GetMapping
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers();
    }

    @Operation(
            summary = "Get user by Id",
            description = "Returns a user for a given User id"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User retrieved successfully",
                    content = @Content(
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping("/{id}")
    public UserResponse getUserById(@Parameter(
                                        description = "Unique ID of the user",
                                        example = "1",
                                        required = true
                                    )
                                    @PathVariable Long id) {
        return userService.getUserById(id);
    }

    @Operation(
            summary = "Update a user",
            description = "Updates an existing user for the given user ID"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User updated successfully",
                    content = @Content(
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PutMapping("/{id}")
    public UserResponse updateUser(
            @Parameter(
                    description = "Unique ID of the user",
                    example = "1",
                    required = true
            )
            @PathVariable Long id,@Valid
                                   @RequestBody UserRequest request) {
        return userService.updateUser(id, request);
    }

    @Operation(
            summary = "Delete a user",
            description = "Deletes an existing user for the given user ID"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "User deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }
}
