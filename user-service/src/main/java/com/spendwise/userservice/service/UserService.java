package com.spendwise.userservice.service;

import com.spendwise.common.exception.ResourceNotFoundException;
import com.spendwise.common.security.CurrentUser;
import com.spendwise.common.security.SecurityAuditLogger;
import com.spendwise.userservice.api.UserRequest;
import com.spendwise.userservice.api.UserResponse;
import com.spendwise.userservice.api.UserSummaryResponse;
import com.spendwise.userservice.domain.UserProfile;
import com.spendwise.userservice.domain.UserProfileRepository;
import com.spendwise.userservice.mapper.UserProfileMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Profiles are identified by the token's {@code sub}. Registration and passwords
 * belong to Keycloak; this service only provisions the SpendWise profile the first
 * time a user calls {@code GET /api/v1/users/me}.
 */
@Service
public class UserService {

    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper userProfileMapper;
    private final String defaultCurrency;

    public UserService(UserProfileRepository userProfileRepository,
                       UserProfileMapper userProfileMapper,
                       @Value("${spendwise.users.default-currency:INR}") String defaultCurrency) {
        this.userProfileRepository = userProfileRepository;
        this.userProfileMapper = userProfileMapper;
        this.defaultCurrency = defaultCurrency;
    }

    /**
     * Deliberately not one transaction: if two first requests race, the loser's
     * insert fails on the primary key in its own transaction and it simply re-reads
     * the winner's row.
     */
    public UserResponse getOrProvision(CurrentUser currentUser, String displayName) {
        return userProfileRepository.findById(currentUser.userId())
                .map(userProfileMapper::toResponse)
                .orElseGet(() -> provision(currentUser, displayName));
    }

    private UserResponse provision(CurrentUser currentUser, String displayName) {
        String fullName = StringUtils.hasText(displayName) ? displayName : currentUser.email();
        try {
            UserProfile saved = userProfileRepository.saveAndFlush(new UserProfile(
                    currentUser.userId(), currentUser.email(), fullName, defaultCurrency));
            return userProfileMapper.toResponse(saved);
        } catch (DataIntegrityViolationException concurrentFirstLogin) {
            return userProfileMapper.toResponse(findOrThrow(currentUser.userId()));
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return userProfileMapper.toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> listForAdmin(CurrentUser admin, Pageable pageable) {
        SecurityAuditLogger.adminAction("LIST_USER_PROFILES", admin.userId().toString(), "page=" + pageable.getPageNumber());
        return userProfileRepository.findAll(pageable).map(userProfileMapper::toSummary);
    }

    @Transactional
    public UserResponse updateOwn(CurrentUser currentUser, UserRequest request) {
        UserProfile userProfile = findOrThrow(currentUser.userId());
        userProfile.updateProfile(request.fullName(), request.preferredCurrency());
        return userProfileMapper.toResponse(userProfile);
    }

    private UserProfile findOrThrow(UUID id) {
        return userProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserProfile", id));
    }
}
