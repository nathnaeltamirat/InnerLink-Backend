package com.negeyachin.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Stateless JWT utility.
 * Stores userId (sub), email, and role inside the token payload.
 */
public final class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(JsonObject jwtConfig) {
        String secret = jwtConfig.getString("secret");
        int hours      = jwtConfig.getInteger("expirationHours", 24);
        this.key           = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs  = (long) hours * 60 * 60 * 1000;
    }

    /** Generate a signed JWT for the given user. */
    public String generate(String userId, String email, String role) {
        return Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .claim("role", role)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(key)
            .compact();
    }

    /**
     * Validate a token and return its claims, or null if invalid/expired.
     */
    public Claims validate(String token) {
        try {
            return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }
}
