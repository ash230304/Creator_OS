package com.creatoros.common.security;

import com.creatoros.auth.entity.User;
import com.creatoros.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SecurityUtils — static helpers for accessing the authenticated user.
 *
 * ── Why a utility class instead of @Autowired in every service? ───────────────
 * Every authenticated endpoint needs to know WHO is calling it.
 * The current user lives in Spring's SecurityContextHolder (thread-local).
 * Rather than repeating the same SecurityContextHolder lookup code in every
 * service method, we centralize it here.
 *
 * ── How the user gets into the SecurityContext ────────────────────────────────
 * JwtAuthFilter (every request) does:
 *   1. Extract JWT from "Authorization: Bearer ..." header
 *   2. Extract userId from JWT claims
 *   3. Load User from DB
 *   4. SecurityContextHolder.getContext().setAuthentication(
 *        new UsernamePasswordAuthenticationToken(user, null, roles)
 *      )
 *
 * So after JwtAuthFilter runs, the principal IS the User entity.
 * SecurityUtils.getCurrentUser() just retrieves it.
 *
 * ── Thread-local safety ───────────────────────────────────────────────────────
 * SecurityContextHolder is thread-local — each HTTP request thread has its
 * own copy. So there's no risk of one user's context leaking to another.
 * Spring clears the context at the end of each request automatically.
 */
public final class SecurityUtils {

    private SecurityUtils() {} // no instantiation

    /**
     * Returns the authenticated User entity for the current request.
     *
     * Usage:
     *   User currentUser = SecurityUtils.getCurrentUser();
     *
     * @throws BusinessException if called from an unauthenticated context
     *         (should never happen on a protected endpoint — Spring would
     *          have already returned 403 before reaching the service layer)
     */
    public static User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User)) {
            throw new BusinessException("No authenticated user found in security context");
        }

        return (User) auth.getPrincipal();
    }

    /**
     * Convenience: get just the current user's UUID.
     * Used when you only need the ID (e.g. for a repository query).
     */
    public static java.util.UUID getCurrentUserId() {
        return getCurrentUser().getId();
    }
}
