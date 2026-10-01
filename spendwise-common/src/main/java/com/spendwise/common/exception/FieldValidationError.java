package com.spendwise.common.exception;

/**
 * One field-level validation failure (Milestone 13 fix), as reported by
 * {@link AbstractGlobalExceptionHandler#handleMethodArgumentNotValid}. Kept
 * generic and framework-adjacent rather than wrapping Spring's own
 * {@code FieldError} directly on the response body: {@code FieldError} drags
 * in its full {@code ObjectError}/{@code MessageSourceResolvable} surface
 * (codes, arguments, a nested default-message resolution chain) that has no
 * business leaking into a client-facing JSON contract — this record exposes
 * exactly the two fields a client actually needs to show the right error next
 * to the right input.
 */
public record FieldValidationError(String field, String message) {
}
