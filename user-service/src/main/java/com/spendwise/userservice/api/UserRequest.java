package com.spendwise.userservice.api;

import com.spendwise.common.validation.OnCreate;
import com.spendwise.common.validation.ValidCurrencyCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Merged create/update request (Milestone 8), replacing the separate
 * {@code CreateUserRequest}/{@code UpdateUserRequest} records this milestone
 * removes — the exact redesign {@code UpdateUserRequest}'s own Javadoc had
 * flagged since Milestone 2 ("Bean Validation groups for create-vs-update
 * semantics are formalized in Milestone 8").
 *
 * {@code @NotBlank(groups = OnCreate.class)} on every field means presence is
 * only enforced when {@code UserController.create} validates with
 * {@code @Validated(OnCreate.class)}; {@code UserController.update} validates
 * with {@code @Validated(OnUpdate.class)} instead, where a {@code null} field
 * is deliberately allowed through to {@link com.spendwise.userservice.domain.UserProfile#updateProfile}'s
 * own null-means-"leave unchanged" semantics.
 *
 * {@code @Size(min = 1, ...)} and {@link ValidCurrencyCode} carry no
 * {@code groups} at all, so they run under the implicit default group on
 * *both* create and update (both {@code OnCreate} and {@code OnUpdate} extend
 * {@code Default} for exactly this reason) — whenever {@code fullName} or
 * {@code preferredCurrency} is present, it must be well-formed, even on a
 * partial update. This also closes a latent bug in the pre-Milestone-8
 * behavior: {@code updateProfile} only skipped a {@code null}, so a request
 * sending {@code fullName: ""} would previously have silently overwritten a
 * valid name with an empty string.
 */
public record UserRequest(
        @NotBlank(groups = OnCreate.class) @Email String email,
        @NotBlank(groups = OnCreate.class) @Size(min = 1, max = 255) String fullName,
        @NotBlank(groups = OnCreate.class) @ValidCurrencyCode String preferredCurrency) {
}
