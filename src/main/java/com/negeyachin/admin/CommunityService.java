package com.negeyachin.admin;

import com.negeyachin.common.DbPool;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.UUID;

/**
 * CommunityService — Requirements 3 & 4
 * ──────────────────────────────────────────────────────────────────────────
 *
 * Requirement 3 — Community Members list with filter + pagination:
 *   getMembers(search, role, active, page, size, sortBy, sortDir)
 *
 * Requirement 4 — User action APIs:
 *   makeVolunteer(userId)    — sets role='volunteer', creates volunteer_profiles row
 *   removeVolunteer(userId)  — sets role='user', sets volunteer_profile offline
 *   deleteUser(userId)       — hard-deletes user (cascades to all child tables per schema FKs)
 *   adjustRole(userId, role) — sets role to any valid value (user|volunteer|admin)
 *   reviewActivity(userId)   — returns full member detail for the activity review panel
 */
public class CommunityService {

    // ═══════════════════════════════════════════════════════════════════════
    //  Requirement 3 — Community Members list
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns a paginated, searchable, filterable list of community members.
     * Mirrors the Community Members table in the admin dashboard image.
     *
     * @param search  partial match on alias OR email (null/blank = all)
     * @param role    filter by role: user|volunteer|admin (null = all)
     * @param active  true=active only, false=suspended only, null=all
     * @param page    0-based page index
     * @param size    page size (default 10)
     * @param sortBy  column name to sort by (default: created_at)
     * @param sortDir asc|desc (default: desc)
     */
    public Future<JsonObject> getMembers(
            String search, String role, Boolean active,
            int page, int size, String sortBy, String sortDir) {

        // ── Validate + sanitise sort params (prevent SQL injection) ────────
        String safeSortBy  = allowedSortColumn(sortBy);
        String safeSortDir = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        int offset         = page * size;

        // ── Build dynamic WHERE clause ─────────────────────────────────────
        StringBuilder where = new StringBuilder("WHERE 1=1 ");
        Tuple params        = Tuple.tuple();

        if (search != null && !search.isBlank()) {
            where.append("AND (LOWER(u.email) LIKE ? OR LOWER(p.alias) LIKE ?) ");
            String pattern = "%" + search.toLowerCase() + "%";
            params.addString(pattern);
            params.addString(pattern);
        }
        if (role != null && !role.isBlank()) {
            if (!role.matches("user|volunteer|admin")) {
                return Future.failedFuture("VALIDATION:Invalid role filter. Must be: user, volunteer, or admin.");
            }
            where.append("AND u.role = ? ");
            params.addString(role.toLowerCase());
        }
        if (active != null) {
            where.append("AND u.is_active = ? ");
            params.addInteger(active ? 1 : 0);
        }

        String baseJoin = """
            FROM users u
            LEFT JOIN profiles p ON p.user_id = u.id
            LEFT JOIN volunteer_profiles vp ON vp.user_id = u.id
            """;

        // ── COUNT query (for pagination metadata) ─────────────────────────
        String countSql = "SELECT COUNT(*) AS total " + baseJoin + where;

        // ── DATA query ────────────────────────────────────────────────────
        // Derives status column:
        //   Suspended → is_active = 0
        //   Volunteer → maps availability_status: online=Online, busy=Away, else=Offline
        //   User      → Online if last_login_at within 15 minutes, else Away
        String dataSql = """
            SELECT
                u.id           AS user_id,
                u.email,
                u.role,
                u.is_active,
                u.created_at,
                u.last_login_at,
                p.alias,
                p.avatar_url,
                vp.availability_status,
                CASE
                    WHEN u.is_active = 0 THEN 'Suspended'
                    WHEN u.role = 'volunteer' THEN
                        CASE vp.availability_status
                            WHEN 'online' THEN 'Online'
                            WHEN 'busy'   THEN 'Away'
                            ELSE 'Offline'
                        END
                    WHEN u.last_login_at >= DATE_SUB(NOW(), INTERVAL 15 MINUTE) THEN 'Online'
                    ELSE 'Away'
                END AS status
            """
            + baseJoin + where
            + "ORDER BY u." + safeSortBy + " " + safeSortDir + " "
            + "LIMIT ? OFFSET ?";

        // Add LIMIT and OFFSET at the end of params for data query
        Tuple dataParams = Tuple.tuple();
        // Copy existing params into a new tuple then add limit/offset
        for (int i = 0; i < params.size(); i++) {
            dataParams.addValue(params.getValue(i));
        }
        dataParams.addInteger(size);
        dataParams.addInteger(offset);

        Future<Long> countFuture = DbPool.pool()
            .preparedQuery(countSql)
            .execute(params)
            .map(rows -> rows.iterator().next().getLong("total"));

        Future<JsonArray> dataFuture = DbPool.pool()
            .preparedQuery(dataSql)
            .execute(dataParams)
            .map(rows -> {
                JsonArray arr = new JsonArray();
                for (Row row : rows) {
                    arr.add(rowToMemberJson(row));
                }
                return arr;
            });

        return Future.all(countFuture, dataFuture).map(cf -> {
            long total    = cf.resultAt(0);
            JsonArray data = cf.resultAt(1);
            int totalPages = (int) Math.ceil((double) total / size);

            return new JsonObject()
                .put("content",       data)
                .put("totalElements", total)
                .put("totalPages",    totalPages)
                .put("currentPage",   page)
                .put("pageSize",      size);
        });
    }

    /** Get a single member by userId. */
    public Future<JsonObject> getMemberById(String userId) {
        return DbPool.pool()
            .preparedQuery("""
                SELECT
                    u.id AS user_id, u.email, u.role, u.is_active,
                    u.created_at, u.last_login_at,
                    p.alias, p.avatar_url,
                    vp.availability_status,
                    CASE
                        WHEN u.is_active = 0 THEN 'Suspended'
                        WHEN u.role = 'volunteer' THEN
                            CASE vp.availability_status
                                WHEN 'online' THEN 'Online'
                                WHEN 'busy'   THEN 'Away'
                                ELSE 'Offline'
                            END
                        WHEN u.last_login_at >= DATE_SUB(NOW(), INTERVAL 15 MINUTE) THEN 'Online'
                        ELSE 'Away'
                    END AS status
                FROM users u
                LEFT JOIN profiles p ON p.user_id = u.id
                LEFT JOIN volunteer_profiles vp ON vp.user_id = u.id
                WHERE u.id = ?
                """)
            .execute(Tuple.of(userId))
            .compose(rows -> {
                if (rows.size() == 0) {
                    return Future.failedFuture("NOT_FOUND:User not found with id: " + userId);
                }
                return Future.succeededFuture(rowToMemberJson(rows.iterator().next()));
            });
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Requirement 4 — User Actions
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ACTION: Make Volunteer
     * Sets user role to 'volunteer' and creates a volunteer_profiles row if absent.
     */
    public Future<JsonObject> makeVolunteer(String userId) {
        return assertUserExists(userId, "volunteer")
            .compose(currentRole -> {
                if ("volunteer".equals(currentRole)) {
                    return Future.failedFuture("BAD_REQUEST:User is already a volunteer.");
                }
                // Update role
                return DbPool.pool()
                    .preparedQuery("UPDATE users SET role = 'volunteer' WHERE id = ?")
                    .execute(Tuple.of(userId))
                    .compose(__ ->
                        // Upsert volunteer_profiles (INSERT IGNORE keeps it idempotent)
                        DbPool.pool()
                            .preparedQuery("""
                                INSERT IGNORE INTO volunteer_profiles
                                    (id, user_id, is_available, availability_status, active_conversation_count)
                                VALUES (?, ?, 0, 'offline', 0)
                                """)
                            .execute(Tuple.of(UUID.randomUUID().toString(), userId))
                    )
                    .compose(__ -> getMemberById(userId));
            });
    }

    /**
     * ACTION: Remove Volunteer
     * Sets user role back to 'user' and deactivates the volunteer_profiles row.
     */
    public Future<JsonObject> removeVolunteer(String userId) {
        return assertUserExists(userId, "not-volunteer")
            .compose(currentRole -> {
                if (!"volunteer".equals(currentRole)) {
                    return Future.failedFuture("BAD_REQUEST:User is not a volunteer.");
                }
                return DbPool.pool()
                    .preparedQuery("UPDATE users SET role = 'user' WHERE id = ?")
                    .execute(Tuple.of(userId))
                    .compose(__ ->
                        DbPool.pool()
                            .preparedQuery("""
                                UPDATE volunteer_profiles
                                SET is_available = 0, availability_status = 'offline'
                                WHERE user_id = ?
                                """)
                            .execute(Tuple.of(userId))
                    )
                    .compose(__ -> getMemberById(userId));
            });
    }

    /**
     * ACTION: Delete User
     * Hard-deletes the user. All child rows (profiles, sessions, volunteer_profiles,
     * peer conversations, etc.) cascade automatically via schema ON DELETE CASCADE / SET NULL.
     * Admin accounts are protected.
     */
    public Future<Void> deleteUser(String userId) {
        return DbPool.pool()
            .preparedQuery("SELECT role FROM users WHERE id = ?")
            .execute(Tuple.of(userId))
            .compose(rows -> {
                if (rows.size() == 0) {
                    return Future.failedFuture("NOT_FOUND:User not found with id: " + userId);
                }
                String role = rows.iterator().next().getString("role");
                if ("admin".equals(role)) {
                    return Future.failedFuture("BAD_REQUEST:Admin accounts cannot be deleted via this endpoint.");
                }
                return DbPool.pool()
                    .preparedQuery("DELETE FROM users WHERE id = ?")
                    .execute(Tuple.of(userId))
                    .mapEmpty();
            });
    }

    /**
     * ACTION: Adjust Role
     * Sets the user's role to any valid value: user | volunteer | admin.
     * If promoting to volunteer, also ensures volunteer_profiles row exists.
     */
    public Future<JsonObject> adjustRole(String userId, String newRole) {
        if (!newRole.matches("user|volunteer|admin")) {
            return Future.failedFuture("VALIDATION:Invalid role '" + newRole + "'. Must be: user, volunteer, or admin.");
        }
        return DbPool.pool()
            .preparedQuery("SELECT id FROM users WHERE id = ?")
            .execute(Tuple.of(userId))
            .compose(rows -> {
                if (rows.size() == 0) {
                    return Future.failedFuture("NOT_FOUND:User not found with id: " + userId);
                }
                return DbPool.pool()
                    .preparedQuery("UPDATE users SET role = ? WHERE id = ?")
                    .execute(Tuple.of(newRole, userId))
                    .compose(__ -> {
                        if ("volunteer".equals(newRole)) {
                            return DbPool.pool()
                                .preparedQuery("""
                                    INSERT IGNORE INTO volunteer_profiles
                                        (id, user_id, is_available, availability_status, active_conversation_count)
                                    VALUES (?, ?, 0, 'offline', 0)
                                    """)
                                .execute(Tuple.of(UUID.randomUUID().toString(), userId))
                                .mapEmpty();
                        }
                        return Future.succeededFuture();
                    })
                    .compose(__ -> getMemberById(userId));
            });
    }

    /**
     * ACTION: Review Activity
     * Returns the member's full enriched profile for the review activity panel.
     * Extend this to join more tables (peer conversations, breathe sessions, etc.)
     * as the review panel grows.
     */
    public Future<JsonObject> reviewActivity(String userId) {
        return DbPool.pool()
            .preparedQuery("""
                SELECT
                    u.id AS user_id,
                    u.email,
                    u.role,
                    u.is_active,
                    u.created_at,
                    u.last_login_at,
                    p.alias,
                    p.avatar_url,
                    p.current_mood,
                    vp.availability_status,
                    vp.active_conversation_count,
                    vp.last_active_at AS vol_last_active,
                    (SELECT COUNT(*) FROM peer_conversations pc
                     WHERE pc.seeker_user_id = u.id OR pc.volunteer_user_id = u.id) AS total_conversations,
                    (SELECT COUNT(*) FROM reflections r WHERE r.user_id = u.id) AS total_reflections,
                    (SELECT COUNT(*) FROM breathe_sessions bs WHERE bs.user_id = u.id) AS total_breathe_sessions,
                    CASE
                        WHEN u.is_active = 0 THEN 'Suspended'
                        WHEN u.role = 'volunteer' THEN
                            CASE vp.availability_status
                                WHEN 'online' THEN 'Online'
                                WHEN 'busy'   THEN 'Away'
                                ELSE 'Offline'
                            END
                        WHEN u.last_login_at >= DATE_SUB(NOW(), INTERVAL 15 MINUTE) THEN 'Online'
                        ELSE 'Away'
                    END AS status
                FROM users u
                LEFT JOIN profiles p ON p.user_id = u.id
                LEFT JOIN volunteer_profiles vp ON vp.user_id = u.id
                WHERE u.id = ?
                """)
            .execute(Tuple.of(userId))
            .compose(rows -> {
                if (rows.size() == 0) {
                    return Future.failedFuture("NOT_FOUND:User not found with id: " + userId);
                }
                Row row = rows.iterator().next();
                JsonObject data = rowToMemberJson(row)
                    .put("currentMood",           row.getString("current_mood"))
                    .put("volLastActiveAt",        row.getLocalDateTime("vol_last_active") != null
                                                       ? row.getLocalDateTime("vol_last_active").toString() : null)
                    .put("totalConversations",     row.getInteger("total_conversations"))
                    .put("totalReflections",       row.getInteger("total_reflections"))
                    .put("totalBreatheSessions",   row.getInteger("total_breathe_sessions"))
                    .put("activeConversationCount",row.getInteger("active_conversation_count"));
                return Future.succeededFuture(data);
            });
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Private helpers
    // ═══════════════════════════════════════════════════════════════════════

    /** Fetches current role; fails with NOT_FOUND if user doesn't exist. */
    private Future<String> assertUserExists(String userId, String context) {
        return DbPool.pool()
            .preparedQuery("SELECT role FROM users WHERE id = ?")
            .execute(Tuple.of(userId))
            .compose(rows -> {
                if (rows.size() == 0) {
                    return Future.failedFuture("NOT_FOUND:User not found with id: " + userId);
                }
                return Future.succeededFuture(rows.iterator().next().getString("role"));
            });
    }

    /** Converts a result Row into a community member JSON object. */
    private JsonObject rowToMemberJson(Row row) {
        return new JsonObject()
            .put("userId",    row.getString("user_id"))
            .put("email",     row.getString("email"))
            .put("alias",     row.getString("alias"))
            .put("avatarUrl", row.getString("avatar_url"))
            .put("role",      row.getString("role"))
            .put("status",    row.getString("status"))
            .put("joinedAt",  row.getLocalDateTime("created_at") != null
                                  ? row.getLocalDateTime("created_at").toString() : null);
    }

    /** Whitelist of columns the client may sort by (prevents SQL injection). */
    private String allowedSortColumn(String col) {
        return switch (col == null ? "" : col.toLowerCase()) {
            case "email"         -> "email";
            case "role"          -> "role";
            case "last_login_at" -> "last_login_at";
            default              -> "created_at";
        };
    }
}
