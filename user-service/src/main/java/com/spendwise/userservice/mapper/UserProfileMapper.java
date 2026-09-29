package com.spendwise.userservice.mapper;

import com.spendwise.userservice.api.UserResponse;
import com.spendwise.userservice.domain.UserProfile;
import org.mapstruct.Mapper;

/**
 * Compile-time-checked entity-to-response-DTO mapping (Milestone 8), replacing
 * the hand-written {@code UserService.toResponse(UserProfile)} this milestone
 * removes. A 1:1 field mapping — every {@link UserResponse} component has a
 * same-named {@link UserProfile} accessor, so no {@code @Mapping} is needed.
 *
 * Deliberately entity-to-response-DTO only, per this milestone's scope
 * boundary: the request side ({@code UserRequest} -> {@code UserProfile})
 * stays a domain-constructor call in {@code UserService}, not a MapStruct
 * mapping, since request-to-entity construction is where domain invariants
 * (e.g. {@code UserProfile}'s controlled {@code updateProfile} mutator) live,
 * and a generic mapper would bypass them.
 */
@Mapper(componentModel = "spring")
public interface UserProfileMapper {

    UserResponse toResponse(UserProfile userProfile);
}
