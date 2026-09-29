package com.spendwise.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom Bean Validation constraint (Milestone 8) asserting that a string is a
 * valid ISO 4217 currency code (e.g. {@code "INR"}, {@code "USD"}) recognized by
 * the JVM's own {@link java.util.Currency} registry, rather than an arbitrary
 * free-text string. Applied to {@code UserRequest.preferredCurrency()} — the only
 * currency-shaped field in the platform.
 *
 * Deliberately null-tolerant (see {@link CurrencyCodeValidator}): presence is a
 * separate concern owned by {@code @NotBlank}, scoped per Bean Validation group,
 * so this constraint composes correctly whether the field is required (create) or
 * optional (partial update) without duplicating that logic itself.
 */
@Documented
@Constraint(validatedBy = CurrencyCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCurrencyCode {

    String message() default "must be a valid ISO 4217 currency code";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
