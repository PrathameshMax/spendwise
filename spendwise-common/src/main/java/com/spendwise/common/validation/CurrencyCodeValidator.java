package com.spendwise.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Currency;

/**
 * Validates {@link ValidCurrencyCode} by delegating to the JVM's own ISO 4217
 * registry ({@link Currency#getInstance(String)}) rather than maintaining a
 * hand-written list of currency codes that would drift out of date.
 *
 * Null-tolerant by convention (Milestone 8): returns {@code true} for a
 * {@code null} value, deliberately leaving the presence check to a separate,
 * group-scoped {@code @NotBlank} annotation on the same field. This lets one
 * constraint definition serve both {@code OnCreate} (where the field is
 * mandatory) and {@code OnUpdate} (where a {@code null} means "leave
 * unchanged") without this validator needing to know which group is active.
 */
public class CurrencyCodeValidator implements ConstraintValidator<ValidCurrencyCode, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        try {
            Currency.getInstance(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
