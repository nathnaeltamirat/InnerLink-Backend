package com.negeyachin.admin;

import com.negeyachin.common.DbPool;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;

import java.time.LocalDateTime;

/**
 * AnalyticsService — Requirement 2
 * ──────────────────────────────────────────────────────────────────────────
 * Provides getDashboardAnalytics() which returns:
 *
 *  1. System Overview counters (matches the 3 stat cards in the dashboard image):
 *       - activeSoulsCount       → COUNT of users WHERE is_active = 1
 *       - volunteersOnlineCount  → COUNT from volunteer_profiles WHERE availability_status = 'online'
 *       - activeConversationsCount → SUM of active_conversation_count from volunteer_profiles
 *
 *  2. Emergency Monitoring panel — top 3 OPEN flags sorted by:
 *       risk_level: high → medium → low  (FIELD() MySQL function)
 *       then oldest first (flagged_at ASC)
 *     Each flag includes the user's anonymous alias from the profiles table.
 */
public class AnalyticsService {

    public Future<JsonObject> getDashboardAnalytics() {

        // ── Query 1: Active souls count ───────────────────────────────────
        Future<Long> activeSoulsFuture = DbPool.pool()
            .query("SELECT COUNT(*) AS cnt FROM users WHERE is_active = 1")
            .execute()
            .map(rows -> rows.iterator().next().getLong("cnt"));

        // ── Query 2: Volunteers online count ─────────────────────────────
        Future<Long> volunteersOnlineFuture = DbPool.pool()
            .query("SELECT COUNT(*) AS cnt FROM volunteer_profiles WHERE availability_status = 'online'")
            .execute()
            .map(rows -> rows.iterator().next().getLong("cnt"));

        // ── Query 3: Active conversations (sum across all volunteer profiles) ──
        Future<Long> activeConvFuture = DbPool.pool()
            .query("SELECT COALESCE(SUM(active_conversation_count), 0) AS total FROM volunteer_profiles")
            .execute()
            .map(rows -> rows.iterator().next().getLong("total"));

        // ── Query 4: Total volunteers (to compute status label) ───────────
        Future<Long> totalVolunteersFuture = DbPool.pool()
            .query("SELECT COUNT(*) AS cnt FROM users WHERE role = 'volunteer'")
            .execute()
            .map(rows -> rows.iterator().next().getLong("cnt"));

        // ── Query 5: Top-3 OPEN emergency flags ordered by severity ───────
        //   Uses MySQL FIELD() to sort: high=1, medium=2, low=3
        //   Left-joins profiles to get alias
        Future<JsonArray> emergencyFlagsFuture = DbPool.pool()
            .query("""
                SELECT
                    ef.id              AS flag_id,
                    ef.user_id,
                    ef.conversation_id,
                    ef.conversation_type,
                    ef.risk_level,
                    ef.flagged_content,
                    ef.status,
                    ef.flagged_at,
                    COALESCE(p.alias,
                        CONCAT('Anonymous#', UPPER(LEFT(ef.user_id, 4)))
                    ) AS alias
                FROM emergency_flags ef
                LEFT JOIN profiles p ON p.user_id = ef.user_id
                WHERE ef.status = 'open'
                ORDER BY
                    FIELD(ef.risk_level, 'high', 'medium', 'low'),
                    ef.flagged_at ASC
                LIMIT 3
                """)
            .execute()
            .map(rows -> {
                JsonArray arr = new JsonArray();
                for (Row row : rows) {
                    arr.add(new JsonObject()
                        .put("flagId",           row.getString("flag_id"))
                        .put("userId",           row.getString("user_id"))
                        .put("alias",            row.getString("alias"))
                        .put("conversationId",   row.getString("conversation_id"))
                        .put("conversationType", row.getString("conversation_type"))
                        .put("riskLevel",        row.getString("risk_level"))
                        .put("flaggedContent",   row.getString("flagged_content"))
                        .put("status",           row.getString("status"))
                        .put("flaggedAt",        row.getLocalDateTime("flagged_at") != null
                                                     ? row.getLocalDateTime("flagged_at").toString()
                                                     : null)
                    );
                }
                return arr;
            });

        // ── Combine all futures ────────────────────────────────────────────
        return Future.all(
            activeSoulsFuture,
            volunteersOnlineFuture,
            activeConvFuture,
            totalVolunteersFuture,
            emergencyFlagsFuture
        ).map(cf -> {
            long activeSouls       = cf.resultAt(0);
            long volunteersOnline  = cf.resultAt(1);
            long activeConvs       = cf.resultAt(2);
            long totalVolunteers   = cf.resultAt(3);
            JsonArray flags        = cf.resultAt(4);

            // Derive status labels (matches dashboard colour logic)
            String volunteersStatus;
            if (totalVolunteers == 0) {
                volunteersStatus = "none";
            } else {
                double ratio = (double) volunteersOnline / totalVolunteers;
                volunteersStatus = ratio >= 0.5 ? "stable" : ratio >= 0.2 ? "low" : "critical";
            }
            String conversationsStatus = activeConvs > 0 ? "active" : "quiet";

            return new JsonObject()
                .put("activeSoulsCount",          activeSouls)
                .put("volunteersOnlineCount",      volunteersOnline)
                .put("activeConversationsCount",   activeConvs)
                .put("volunteersStatus",           volunteersStatus)
                .put("conversationsStatus",        conversationsStatus)
                .put("emergencyMonitoring",        flags)
                .put("snapshotTime",               LocalDateTime.now().toString());
        });
    }
}
