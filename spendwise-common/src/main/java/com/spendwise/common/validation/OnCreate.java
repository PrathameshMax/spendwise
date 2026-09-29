package com.spendwise.common.validation;

import jakarta.validation.groups.Default;

/**
 * Bean Validation group (Milestone 8) selecting the constraints that apply only
 * when a resource is being created — e.g. a field that is mandatory on create
 * but left untouched (and therefore not required) on a partial update, such as
 * {@code UserRequest.email()}.
 *
 * Extends {@link Default} deliberately: Jakarta Bean Validation does not run the
 * implicit {@code Default} group automatically once any custom group is named on
 * {@code @Validated}/{@code @Valid} — only the groups explicitly listed run. Without
 * this {@code extends Default}, every constraint left in the default group (such as
 * an un-grouped {@code @Size} meant to apply on both create and update) would be
 * silently skipped whenever {@code @Validated(OnCreate.class)} is used, since
 * {@code OnCreate} alone would not imply {@code Default}.
 */
public interface OnCreate extends Default {
}
