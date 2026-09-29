package com.spendwise.authservice.mapper;

import com.spendwise.authservice.api.CredentialResponse;
import com.spendwise.authservice.domain.Credential;
import org.mapstruct.Mapper;

/**
 * Compile-time-checked entity-to-response-DTO mapping (Milestone 8), replacing
 * the hand-written {@code AuthService.toResponse(Credential)} this milestone
 * removes. A 1:1 field mapping (no nested properties to flatten), but kept as
 * a MapStruct mapper rather than left as a manual method for consistency with
 * every other service's response mapping and to catch a field being added to
 * {@link Credential} without a corresponding {@link CredentialResponse}
 * component at compile time rather than silently omitting it at runtime.
 *
 * {@code componentModel = "spring"} makes MapStruct generate this as a Spring
 * {@code @Component} ({@code CredentialMapperImpl}) so it can be constructor-
 * injected like any other collaborator, rather than accessed via a generated
 * static {@code INSTANCE} field.
 */
@Mapper(componentModel = "spring")
public interface CredentialMapper {

    CredentialResponse toResponse(Credential credential);
}
