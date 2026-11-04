package com.spendwise.userservice.mapper;

import com.spendwise.userservice.api.UserResponse;
import com.spendwise.userservice.api.UserSummaryResponse;
import com.spendwise.userservice.domain.UserProfile;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserProfileMapper {

    UserResponse toResponse(UserProfile userProfile);

    UserSummaryResponse toSummary(UserProfile userProfile);
}
