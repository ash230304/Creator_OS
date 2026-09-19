package com.creatoros.common.security;

import com.creatoros.auth.entity.User;
import com.creatoros.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * JwtAuthFilter — intercepts every HTTP request and validates the JWT.
 *
 * ── How filters work in Spring ───────────────────────────────────────────────
 * Spring Security wraps your app in a "filter chain" — a series of filters
 * each HTTP request passes through before reaching a controller.
 *
 * OncePerRequestFilter guarantees this filter runs exactly once per request,
 * even with async dispatches (Spring can re-dispatch internally).
 *
 * ── What this filter does ────────────────────────────────────────────────────
 * For every request:
 *
 * 1. Extract the JWT from the Authorization header
 *    Header format: "Authorization: Bearer eyJhbGci..."
 *    We strip the "Bearer " prefix to get the raw token.
 *
 * 2. Validate the token (via JwtUtil.isTokenValid)
 *    - Signature must match
 *    - Token must not be expired
 *
 * 3. Extract the userId (the "sub" claim)
 *
 * 4. Load the User from the database
 *    - If the user was deleted after the token was issued → request fails
 *
 * 5. Set authentication in the SecurityContext
 *    This is what tells Spring "this request is authenticated as this user".
 *    Any subsequent code in the same request can call:
 *      SecurityContextHolder.getContext().getAuthentication()
 *    to get the current user.
 *
 * 6. Call filterChain.doFilter() to pass the request along
 *
 * ── What happens if any step fails ───────────────────────────────────────────
 * We simply don't set authentication and call doFilter() anyway.
 * The AuthorizationFilter later in the chain will see no authentication
 * and return 403 / redirect to 401, depending on configuration.
 * We never write the response here — that keeps the filter generic.
 *
 * ── SecurityContextHolder ─────────────────────────────────────────────────
 * Spring's thread-local storage for the current user.
 * Think of it like:
 *   "For this thread (= this request), the logged-in user is X."
 * After the request completes, Spring automatically clears it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil        jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain)
            throws ServletException, IOException {

        // ── Step 1: Extract token from header ─────────────────────────────
        String token = extractToken(request);

        if (token != null && jwtUtil.isTokenValid(token)) {

            // ── Step 2: Get userId from token ──────────────────────────────
            UUID userId = jwtUtil.extractUserId(token);

            // ── Step 3: Load user from DB (confirms user still exists) ─────
            // Only proceed if there's no existing auth in context
            // (prevents overwriting auth set by another mechanism)
            if (SecurityContextHolder.getContext().getAuthentication() == null) {

                User user = userRepository.findById(userId).orElse(null);

                if (user != null) {
                    // ── Step 4: Build authentication object ─────────────────
                    // UsernamePasswordAuthenticationToken is Spring's standard
                    // auth object. We pass:
                    //   principal   = the User entity itself
                    //   credentials = null (we don't need the password anymore)
                    //   authorities = ["ROLE_USER"] (basic role for now)
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    user,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
                            );

                    // Attaches request metadata (IP, session id) to the auth object
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // ── Step 5: Set in SecurityContext ──────────────────────
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }

        // ── Step 6: Always continue the filter chain ───────────────────────
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the raw JWT string from the Authorization header.
     * Returns null if the header is missing or not in "Bearer ..." format.
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        // StringUtils.hasText() → not null, not empty, not whitespace only
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);  // strip "Bearer " (7 characters)
        }
        return null;
    }
}
