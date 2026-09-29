package com.spendwise.common.validation;

import jakarta.validation.groups.Default;

/**
 * Bean Validation group (Milestone 8) selecting the constraints that apply only
 * when a resource is being partially updated — e.g. a field that is optional on
 * update (a {@code null} leaves the existing value untouched, per
 * {@code UserProfile.updateProfile}) but mandatory on create, such as
 * {@code UserRequest.email()}.
 *
 * Extends {@link Default} for the same reason as {@link OnCreate}: without it,
 * un-grouped constraints (e.g. a {@code @Size} that should still be checked
 * whenever a field is present, on either create or update) would be silently
 * skipped whenever {@code @Validated(OnUpdate.class)} is used.
 */
public interface OnUpdate extends Default {
}
