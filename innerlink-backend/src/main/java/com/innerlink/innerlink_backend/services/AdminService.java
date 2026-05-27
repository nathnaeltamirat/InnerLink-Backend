package com.innerlink.innerlink_backend.services;

import com.innerlink.innerlink_backend.config.DatabaseConfig;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.ArrayList;
import java.util.List;

public class AdminService {

    public Future<JsonObject> fetchAnalytics() {
        JsonObject result = new JsonObject();

        Future<Long> soulsCount = DatabaseConfig.getClient()
            .query("SELECT COUNT(*) AS total FROM users WHERE role = 'user'")
            .execute()
            .map(rows -> rows.iterator().next().getLong("total"));

        Future<Long> volunteersOnline = DatabaseConfig.getClient()
            .query("SELECT COUNT(*) AS total FROM users WHERE role = 'volunteer' AND is_available = 1")
            .execute()
            .map(rows -> rows.iterator().next().getLong("total"));

        Future<Long> liveConversations = DatabaseConfig.getClient()
            .query("SELECT COUNT(*) AS total FROM conversations WHERE is_active = 1")
            .execute()
            .map(rows -> rows.iterator().next().getLong("total"));

        Future<JsonArray> activeFlags = DatabaseConfig.getClient()
            .query("SELECT f.id, f.user_id, f.risk_level, f.flagged_content, f.flagged_at, u.alias " +
                   "FROM emergency_flags f JOIN users u ON f.user_id = u.id WHERE f.status = 'open' " +
                   "ORDER BY f.flagged_at DESC")
            .execute()
            .map(rows -> {
                JsonArray arr = new JsonArray();
                for (Row row : rows) {
                    arr.add(new JsonObject()
                        .put("flagId", row.getString("id"))
                        .put("userId", row.getString("user_id"))
                        .put("riskLevel", row.getString("risk_level"))
                        .put("flaggedContent", row.getString("flagged_content"))
                        .put("alias", row.getString("alias"))
                        .put("flaggedAt", row.getLocalDateTime("flagged_at") != null ? row.getLocalDateTime("flagged_at").toString() : null)
                    );
                }
                return arr;
            });

        return Future.all(soulsCount, volunteersOnline, liveConversations, activeFlags)
            .map(composite -> result
                .put("activeSoulsCount", composite.resultAt(0))
                .put("volunteersOnlineCount", composite.resultAt(1))
                .put("activeConversationsCount", composite.resultAt(2))
                .put("emergencyMonitoring", composite.resultAt(3))
                .put("volunteersStatus", ((Long) composite.resultAt(1)) > 0 ? "stable" : "critical")
                .put("conversationsStatus", "active")
            );
    }

    public Future<JsonObject> fetchMembers(int page, int size, String search, String role, String activeParam, String sortBy) {
        StringBuilder queryBuilder = new StringBuilder("FROM users WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (search != null && !search.isBlank()) {
            queryBuilder.append(" AND (email LIKE ? OR alias LIKE ?)");
            params.add("%" + search + "%");
            params.add("%" + search + "%");
        }
        if (role != null && !role.isBlank()) {
            queryBuilder.append(" AND role = ?");
            params.add(role);
        }
        if (activeParam != null && !activeParam.isBlank()) {
            queryBuilder.append(" AND is_available = ?");
            params.add("1".equals(activeParam) || "true".equalsIgnoreCase(activeParam) ? 1 : 0);
        }

        String baseCondition = queryBuilder.toString();
        String orderField = "created_at";
        if ("alias".equalsIgnoreCase(sortBy) || "email".equalsIgnoreCase(sortBy) || "role".equalsIgnoreCase(sortBy)) {
            orderField = sortBy;
        }

        String dataQuery = "SELECT id, email, alias, role, is_available, created_at " + baseCondition +
                           " ORDER BY " + orderField + " DESC LIMIT ? OFFSET ?";
        String countQuery = "SELECT COUNT(*) AS total " + baseCondition;

        List<Object> dataArgs = new ArrayList<>(params);
        dataArgs.add(size);
        dataArgs.add(page * size);

        Future<Long> totalElementsFuture = DatabaseConfig.getClient().preparedQuery(countQuery).execute(Tuple.from(params))
            .map(rows -> rows.iterator().next().getLong("total"));

        Future<JsonArray> contentFuture = DatabaseConfig.getClient().preparedQuery(dataQuery).execute(Tuple.from(dataArgs))
            .map(rows -> {
                JsonArray members = new JsonArray();
                for (Row r : rows) {
                    members.add(new JsonObject()
                        .put("userId", r.getString("id"))
                        .put("email", r.getString("email"))
                        .put("alias", r.getString("alias"))
                        .put("role", r.getString("role"))
                        .put("status", Boolean.TRUE.equals(r.getBoolean("is_available")) ? "Online" : "Away")
                        .put("joinedAt", r.getLocalDateTime("created_at") != null ? r.getLocalDateTime("created_at").toString() : null)
                    );
                }
                return members;
            });

        return Future.all(totalElementsFuture, contentFuture).map(composite -> {
            long totalElements = composite.resultAt(0);
            JsonArray content = composite.resultAt(1);
            int totalPages = (int) Math.ceil((double) totalElements / size);

            return new JsonObject()
                .put("content", content)
                .put("totalElements", totalElements)
                .put("currentPage", page)
                .put("totalPages", totalPages == 0 ? 1 : totalPages);
        });
    }

    public Future<JsonObject> fetchMemberActivity(String userId) {
        String userSql = "SELECT email, alias, role, is_available, current_mood, created_at FROM users WHERE id = ?";
        
        return DatabaseConfig.getClient().preparedQuery(userSql).execute(Tuple.of(userId))
            .compose(rows -> {
                if (!rows.iterator().hasNext()) {
                    return Future.failedFuture("No active account matching profile keys found.");
                }
                Row r = rows.iterator().next();
                JsonObject profile = new JsonObject()
                    .put("email", r.getString("email"))
                    .put("alias", r.getString("alias"))
                    .put("role", r.getString("role"))
                    .put("status", Boolean.TRUE.equals(r.getBoolean("is_available")) ? "Online" : "Away")
                    .put("joinedAt", r.getLocalDateTime("created_at") != null ? r.getLocalDateTime("created_at").toString() : null)
                    .put("currentMood", r.getString("current_mood"));

                Future<Long> activeConvs = DatabaseConfig.getClient()
                    .preparedQuery("SELECT COUNT(*) as total FROM conversation_participants WHERE user_id = ?")
                    .execute(Tuple.of(userId)).map(res -> res.iterator().next().getLong("total"));

                Future<Long> totalReflections = DatabaseConfig.getClient()
                    .preparedQuery("SELECT COUNT(*) as total FROM reflections WHERE user_id = ?")
                    .execute(Tuple.of(userId)).map(res -> res.iterator().next().getLong("total"));

                return Future.all(activeConvs, totalReflections).map(composite -> profile
                    .put("totalConversations", composite.resultAt(0))
                    .put("totalReflections", composite.resultAt(1))
                    .put("totalBreatheSessions", 0)
                    .put("activeConversationCount", composite.resultAt(0))
                );
            });
    }

    public Future<Void> modifyMemberRole(String userId, String targetRole) {
        String sql = "UPDATE users SET role = ? WHERE id = ?";
        return DatabaseConfig.getClient().preparedQuery(sql).execute(Tuple.of(targetRole, userId)).mapEmpty();
    }

    public Future<Void> removeUserAccount(String userId) {
        String sql = "DELETE FROM users WHERE id = ?";
        return DatabaseConfig.getClient().preparedQuery(sql).execute(Tuple.of(userId)).mapEmpty();
    }

    public Future<JsonArray> fetchEmergencyFlagsFeed() {
        String sql = "SELECT f.id, f.user_id, f.conversation_id, f.risk_level, f.flagged_content, f.status, f.flagged_at, u.alias " +
                     "FROM emergency_flags f JOIN users u ON f.user_id = u.id " +
                     "ORDER BY CASE f.status WHEN 'open' THEN 1 WHEN 'under_review' THEN 2 ELSE 3 END ASC, f.flagged_at DESC";
                     
        return DatabaseConfig.getClient().query(sql).execute()
            .map(rows -> {
                JsonArray feed = new JsonArray();
                for (Row row : rows) {
                    feed.add(new JsonObject()
                        .put("flagId", row.getString("id"))
                        .put("userId", row.getString("user_id"))
                        .put("conversationId", row.getString("conversation_id"))
                        .put("riskLevel", row.getString("risk_level"))
                        .put("flaggedContent", row.getString("flagged_content"))
                        .put("status", row.getString("status"))
                        .put("flaggedAt", row.getLocalDateTime("flagged_at") != null ? row.getLocalDateTime("flagged_at").toString() : null)
                        .put("alias", row.getString("alias"))
                    );
                }
                return feed;
            });
    }

    public Future<Void> changeFlagTriageStatus(String flagId, String targetStatus) {
        String sql = "UPDATE emergency_flags SET status = ?, resolved_at = IF(? = 'resolved', CURRENT_TIMESTAMP, resolved_at) WHERE id = ?";
        return DatabaseConfig.getClient().preparedQuery(sql)
            .execute(Tuple.of(targetStatus, targetStatus, flagId))
            .mapEmpty();
    }
}