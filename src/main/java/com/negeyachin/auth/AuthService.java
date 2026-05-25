package com.negeyachin.auth;

import com.negeyachin.common.DbPool;
import com.negeyachin.util.JwtUtil;
import at.favre.lib.crypto.bcrypt.BCrypt;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AuthService
 * ──────────────────────────────────────────────────────────────
 * Handles all authentication logic:
 *   - signUp   : validates email uniqueness, hashes passkey with BCrypt,
 *                inserts into users + profiles, persists a session, returns JWT.
 *   - signIn   : verifies credentials, updates last_login_at,
 *                persists a session row, returns JWT.
 *   - signOut  : revokes all session rows for the user (sets is_revoked = 1).
 *
 * All DB operations use the Vert.x reactive MySQL client (non-blocking).
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final JwtUtil jwtUtil;

    public AuthService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SIGN UP
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Registers a new user.
     * Steps:
     *   1. Check email uniqueness.
     *   2. Hash passkey with BCrypt (cost 12) + generate a random salt UUID.
     *   3. INSERT into users.
     *   4. INSERT into profiles with anonymous alias.
     *   5. INSERT session row.
     *   6. Return JWT + user info.
     *
     * @param body  { "email": "...", "passkey": "...", "alias": "..." (optional) }
     * @return Future<JsonObject> containing token and user details
     */
    public Future<JsonObject> signUp(JsonObject body) {
        String email   = body.getString("email",   "").trim().toLowerCase();
        String passkey = body.getString("passkey", "").trim();
        String alias   = body.getString("alias",   "").trim();

        // ── Validate input ────────────────────────────────────────────────
        if (email.isBlank() || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return Future.failedFuture("VALIDATION:Email must be a valid email address.");
        }
        if (passkey.length() < 8) {
            return Future.failedFuture("VALIDATION:Passkey must be at least 8 characters.");
        }

        // ── Check email uniqueness ────────────────────────────────────────
        return DbPool.pool()
            .preparedQuery("SELECT id FROM users WHERE email = ?")
            .execute(Tuple.of(email))
            .compose(rows -> {
                if (rows.size() > 0) {
                    return Future.failedFuture("CONFLICT:An account with this email already exists.");
                }

                // ── Hash passkey (BCrypt cost 12) ─────────────────────────
                String salt = UUID.randomUUID().toString();
                String hash = BCrypt.withDefaults().hashToString(12, passkey.toCharArray());

                String userId    = UUID.randomUUID().toString();
                String profileId = UUID.randomUUID().toString();
                String finalAlias = alias.isBlank()
                    ? "Anonymous#" + userId.substring(0, 4).toUpperCase()
                    : alias;

                // ── INSERT user ───────────────────────────────────────────
                return DbPool.pool()
                    .preparedQuery("""
                        INSERT INTO users (id, email, passkey_hash, passkey_salt, role, is_active, created_at)
                        VALUES (?, ?, ?, ?, 'user', 1, NOW())
                        """)
                    .execute(Tuple.of(userId, email, hash, salt))
                    .compose(__ ->
                        // ── INSERT profile ────────────────────────────────
                        DbPool.pool()
                            .preparedQuery("""
                                INSERT INTO profiles (id, user_id, alias, is_anonymous, updated_at)
                                VALUES (?, ?, ?, 1, NOW())
                                """)
                            .execute(Tuple.of(profileId, userId, finalAlias))
                    )
                    .compose(__ -> {
                        // ── Generate JWT + INSERT session ──────────────────
                        String token     = jwtUtil.generate(userId, email, "user");
                        String sessionId = UUID.randomUUID().toString();
                        return DbPool.pool()
                            .preparedQuery("""
                                INSERT INTO sessions (id, user_id, token, is_revoked, created_at, expires_at)
                                VALUES (?, ?, ?, 0, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY))
                                """)
                            .execute(Tuple.of(sessionId, userId, token))
                            .map(__ -> new JsonObject()
                                .put("token",  token)
                                .put("userId", userId)
                                .put("email",  email)
                                .put("role",   "user")
                                .put("alias",  finalAlias)
                                .put("message", "Welcome to your digital sanctuary.")
                            );
                    });
            });
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SIGN IN
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Authenticates an existing user.
     * Steps:
     *   1. Fetch user by email.
     *   2. Check is_active.
     *   3. Verify passkey against BCrypt hash.
     *   4. Update last_login_at.
     *   5. INSERT session row.
     *   6. Fetch alias from profiles.
     *   7. Return JWT + user info.
     *
     * @param body        { "email": "...", "passkey": "..." }
     * @param ipAddress   client IP (stored in sessions)
     * @param userAgent   client UA (stored in sessions)
     */
    public Future<JsonObject> signIn(JsonObject body, String ipAddress, String userAgent) {
        String email   = body.getString("email",   "").trim().toLowerCase();
        String passkey = body.getString("passkey", "").trim();

        if (email.isBlank() || passkey.isBlank()) {
            return Future.failedFuture("VALIDATION:Email and passkey are required.");
        }

        return DbPool.pool()
            .preparedQuery("""
                SELECT id, passkey_hash, role, is_active
                FROM users WHERE email = ?
                """)
            .execute(Tuple.of(email))
            .compose(rows -> {
                if (rows.size() == 0) {
                    // Use same message for missing user and wrong passkey (prevents email enumeration)
                    return Future.failedFuture("UNAUTHORIZED:Invalid credentials.");
                }

                Row row      = rows.iterator().next();
                String userId     = row.getString("id");
                String storedHash = row.getString("passkey_hash");
                String role       = row.getString("role");
                boolean isActive  = row.getInteger("is_active") == 1;

                if (!isActive) {
                    return Future.failedFuture("UNAUTHORIZED:This account has been suspended.");
                }

                // ── Verify BCrypt hash ────────────────────────────────────
                BCrypt.Result result = BCrypt.verifyer().verify(passkey.toCharArray(), storedHash);
                if (!result.verified) {
                    return Future.failedFuture("UNAUTHORIZED:Invalid credentials.");
                }

                // ── Update last_login_at ──────────────────────────────────
                return DbPool.pool()
                    .preparedQuery("UPDATE users SET last_login_at = NOW() WHERE id = ?")
                    .execute(Tuple.of(userId))
                    .compose(__ -> {
                        String token     = jwtUtil.generate(userId, email, role);
                        String sessionId = UUID.randomUUID().toString();

                        // ── INSERT session ────────────────────────────────
                        return DbPool.pool()
                            .preparedQuery("""
                                INSERT INTO sessions
                                  (id, user_id, token, ip_address, user_agent, is_revoked, created_at, expires_at)
                                VALUES (?, ?, ?, ?, ?, 0, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY))
                                """)
                            .execute(Tuple.of(sessionId, userId, token, ipAddress, userAgent))
                            .compose(___ ->
                                // ── Fetch alias ───────────────────────────
                                DbPool.pool()
                                    .preparedQuery("SELECT alias FROM profiles WHERE user_id = ?")
                                    .execute(Tuple.of(userId))
                                    .map(profileRows -> {
                                        String alias = profileRows.size() > 0
                                            ? profileRows.iterator().next().getString("alias")
                                            : null;
                                        return new JsonObject()
                                            .put("token",  token)
                                            .put("userId", userId)
                                            .put("email",  email)
                                            .put("role",   role)
                                            .put("alias",  alias)
                                            .put("message", "Enter quietly.");
                                    })
                            );
                    });
            });
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SIGN OUT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Revokes all active sessions for the user (sets is_revoked = 1).
     *
     * @param userId the authenticated user's ID (from JWT)
     */
    public Future<Void> signOut(String userId) {
        return DbPool.pool()
            .preparedQuery("UPDATE sessions SET is_revoked = 1 WHERE user_id = ? AND is_revoked = 0")
            .execute(Tuple.of(userId))
            .mapEmpty();
    }
}
