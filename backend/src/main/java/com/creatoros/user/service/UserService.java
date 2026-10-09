package com.creatoros.user.service;

import com.creatoros.auth.entity.User;
import com.creatoros.auth.repository.UserRepository;
import com.creatoros.common.exception.ResourceNotFoundException;
import com.creatoros.common.security.SecurityUtils;
import com.creatoros.user.dto.UpdateUserRequest;
import com.creatoros.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserService — profile management for the authenticated user.
 *
 * ── Why does UserService use UserRepository from auth package? ────────────────
 * The User entity lives in com.creatoros.auth.entity because auth was built first.
 * In a larger codebase you'd move User to com.creatoros.user.entity.
 * For V1 this is acceptable — auth and user are closely related.
 * The UserRepository stays in auth and we import it here.
 *
 * ── Why no "update email" or "change password" here? ─────────────────────────
 * Email change requires re-verification → email round-trip → out of scope for V1.
 * Password change requires verifying the current password first — different flow.
 * Both would be separate endpoints in V2.
 *
 * ── Native update via @Modifying query vs dirty checking ─────────────────────
 * We use Hibernate dirty checking here: load entity, mutate, let transaction commit.
 * An alternative is a @Query("UPDATE users SET ...") — faster for bulk updates
 * but bypasses entity-level validation. For single-user profile updates,
 * dirty checking is readable and safe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // ── GET PROFILE ──────────────────────────────────────────────────────────

    /**
     * Returns the current authenticated user's profile.
     * SecurityUtils.getCurrentUser() gets the User from the JWT context.
     * We re-fetch from DB to get the latest data (not just what's in the token).
     */
    @Transactional(readOnly = true)
    public UserResponse getMe() {
        User user = SecurityUtils.getCurrentUser();

        // Re-fetch from DB in case bio/avatarUrl was updated after login
        User fresh = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        log.debug("Profile fetched for user {}", fresh.getId());
        return UserResponse.from(fresh);
    }

    // ── UPDATE PROFILE ───────────────────────────────────────────────────────

    /**
     * Updates the current user's name, bio, and/or avatarUrl.
     * Any null field in the request means "keep existing value".
     * Returns the updated profile as UserResponse.
     */
    @Transactional
    public UserResponse updateMe(UpdateUserRequest request) {
        User user = SecurityUtils.getCurrentUser();

        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Apply partial update — only change what was sent
        managed.updateProfile(
                request.name()      != null ? request.name()      : managed.getName(),
                request.bio()       != null ? request.bio()       : managed.getBio(),
                request.avatarUrl() != null ? request.avatarUrl() : managed.getAvatarUrl()
        );

        // Dirty checking: Hibernate detects the changed fields and generates UPDATE SQL
        // on transaction commit — no explicit save() needed.
        log.info("Profile updated for user {}", managed.getId());
        return UserResponse.from(managed);
    }
}
