package com.spendwise.userservice.api;

import com.spendwise.common.security.CurrentUser;
import com.spendwise.common.validation.OnUpdate;
import com.spendwise.userservice.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Hop ③ and ④ in one controller:
 * <ul>
 *   <li>{@code /me} — the profile is always the caller's, resolved from the token's
 *       {@code sub}; there is no id to tamper with.</li>
 *   <li>{@code /{id}} — method-level rule: an admin, an internal service (client
 *       credentials, {@code ROLE_SERVICE}) or the owner themself; anyone else 403.</li>
 *   <li>{@code /} — admins only, with emails masked in the response.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN') and hasAuthority('SCOPE_users:read')")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.getOrProvision(CurrentUser.require(), jwt.getClaimAsString("name")));
    }

    @PatchMapping("/me")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN') and hasAuthority('SCOPE_users:write')")
    public ResponseEntity<UserResponse> updateMe(@Validated(OnUpdate.class) @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.updateOwn(CurrentUser.require(), request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE') or #id.toString() == authentication.name")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserSummaryResponse>> list(Pageable pageable) {
        return ResponseEntity.ok(userService.listForAdmin(CurrentUser.require(), pageable));
    }
}
