package com.creatoros.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * JwtUtil — generates and validates JSON Web Tokens.
 *
 * ── What is a JWT? ──────────────────────────────────────────────────────────
 * A JWT has 3 parts separated by dots:
 *
 *   eyJhbGciOiJIUzI1NiJ9          ← Header (Base64): algorithm = HS256
 *   .
 *   eyJzdWIiOiJ1c2VyLXV1aWQiLC...  ← Payload (Base64): the "claims" (data)
 *   .
 *   SflKxwRJSMeKKF2QT4fwpMeJf36... ← Signature: HMAC-SHA256(header+payload, secret)
 *
 * The signature is what makes it tamper-proof. If anyone changes the payload
 * (e.g. tries to change the userId), the signature check fails.
 *
 * ── Claims we store in the payload ──────────────────────────────────────────
 *   sub  (Subject)    = userId (UUID as string) — standard JWT claim
 *   iat  (Issued At)  = timestamp when token was issued — standard
 *   exp  (Expiration) = timestamp when token expires — standard
 *
 * We intentionally keep claims minimal. The userId is enough —
 * the server can look up the rest from the database.
 *
 * ── Key management ──────────────────────────────────────────────────────────
 * The secret key comes from application.yml:
 *   jwt.secret: ${JWT_SECRET:...}
 *
 * It's Base64-encoded and decoded at startup.
 * The key must be at least 256 bits (32 bytes) for HS256.
 *
 * ── Library: io.jsonwebtoken (jjwt) ─────────────────────────────────────────
 * JJWT is the most widely used JWT library for Java.
 * API:
 *   Jwts.builder() ... .compact()     → create a token string
 *   Jwts.parser()  ... .parseSignedClaims() → validate + read claims
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String base64Secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        // Decode the Base64 secret into raw bytes, then wrap as an HMAC-SHA key
        this.secretKey  = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.expirationMs = expirationMs;
    }

    // ── Token Generation ────────────────────────────────────────────────────

    /**
     * Generate a signed JWT for a given userId.
     *
     * @param userId the authenticated user's UUID
     * @return compact JWT string: "eyJ...eyJ...SfK..."
     */
    public String generateToken(UUID userId) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userId.toString())   // "sub" claim — who this token is for
                .issuedAt(now)                 // "iat" claim
                .expiration(expiry)            // "exp" claim
                .signWith(secretKey)           // signs with HS256 using our secret
                .compact();                    // serialize to the 3-part string
    }

    // ── Token Validation ────────────────────────────────────────────────────

    /**
     * Parse and validate a JWT string.
     * Throws JwtException subtypes on any failure (expired, bad sig, malformed).
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)           // tell the parser which key to verify with
                .build()
                .parseSignedClaims(token)        // parse, verify signature, check expiry
                .getPayload();                   // return the claims (payload)
    }

    /**
     * Extract the userId (stored in "sub" claim) from a validated token.
     */
    public UUID extractUserId(String token) {
        return UUID.fromString(parseToken(token).getSubject());
    }

    /**
     * Returns true only if the token is valid (signature OK + not expired).
     * Used in JwtAuthFilter — if false, the request is rejected with 401.
     */
    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT malformed: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("JWT signature invalid: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims empty: {}", e.getMessage());
        }
        return false;
    }
}
