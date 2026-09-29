package com.spendwise.userservice.api;

import com.spendwise.common.validation.OnCreate;
import com.spendwise.common.validation.OnUpdate;
import com.spendwise.userservice.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * {@code @Validated(OnCreate.class)}/{@code @Validated(OnUpdate.class)}
 * (Milestone 8) select which Bean Validation group applies per endpoint —
 * plain {@code @Valid} cannot specify a group, which is exactly why Spring's
 * own {@code @Validated} is used here instead of jakarta's {@code @Valid}
 * (used everywhere else in the platform where there is only one group).
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Validated(OnCreate.class) @RequestBody UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    /**
     * Paginated, sortable listing — e.g. GET /api/v1/users?page=0&size=20&sort=fullName,asc
     */
    @GetMapping
    public ResponseEntity<Page<UserResponse>> list(Pageable pageable) {
        return ResponseEntity.ok(userService.list(pageable));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UserResponse> update(
            @PathVariable UUID id, @Validated(OnUpdate.class) @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }
}
