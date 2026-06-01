package com.innerlink.innerlink_backend.chat.matching;

import com.innerlink.innerlink_backend.config.DatabaseConfig;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.Row;

public class VolunteerTalkingService {

    private final SqlClient client = DatabaseConfig.getClient();

    /**
     * Helper to generate standardized conversation indices
     */
    private static String generateId(String prefix) {
        return prefix + "_" +
                java.time.LocalDate.now() + "_" +
                java.util.UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    /**
     * Establishes a permanent support room mapping channel link 
     * between a specific user and an assigned volunteer.
     */
    public Future<JsonObject> talkToVolunteer(String volunteerId, String userId) {
        String conversationId = generateId("Conv");
        String chatTitle = "Volunteer Chat Line";

        return client.preparedQuery("""
                SELECT c.id AS conversation_id, COALESCE(v.alias, c.title) AS title
                FROM conversations c
                JOIN conversation_participants user_cp ON c.id = user_cp.conversation_id
                JOIN conversation_participants volunteer_cp ON c.id = volunteer_cp.conversation_id
                JOIN users v ON volunteer_cp.user_id = v.id
                WHERE c.type = 'support'
                    AND c.is_active = 1
                    AND user_cp.user_id = ?
                    AND volunteer_cp.user_id = ?
                    AND v.role = 'volunteer'
                LIMIT 1
            """).execute(Tuple.of(userId, volunteerId))
            .compose(existingRows -> {
                if (existingRows.size() > 0) {
                    Row row = existingRows.iterator().next();
                    return Future.succeededFuture(new JsonObject()
                            .put("success", true)
                            .put("status", "connected")
                            .put("conversationId", row.getString("conversation_id"))
                            .put("title", row.getString("title"))
                            .put("userId", userId)
                            .put("volunteerId", volunteerId));
                }

                return client.preparedQuery("""
                INSERT IGNORE INTO conversations(id, type, title)
                VALUES(?, ?, ?)
            """).execute(Tuple.of(conversationId, "support", chatTitle))
                    .compose(v -> client.preparedQuery("""
                    INSERT IGNORE INTO conversation_participants (conversation_id, user_id, role)
                    VALUES (?, ?, ?), (?, ?, ?)
                """).execute(Tuple.of(
                            conversationId, userId, "member",
                            conversationId, volunteerId, "volunteer"
                )))
                    .map(v -> new JsonObject()
                            .put("success", true)
                            .put("status", "connected")
                            .put("conversationId", conversationId)
                            .put("title", chatTitle)
                            .put("userId", userId)
                            .put("volunteerId", volunteerId));
            })
            .onFailure(err -> System.err.println("Database Transaction Failed during room allocation: " + err.getMessage()));
    }

    public Future<JsonArray> getAvailableVolunteers(String userId) {
        return client.preparedQuery("""
                SELECT id, alias
                FROM users
                WHERE role = 'volunteer' AND is_available = 1 AND id != ?
                ORDER BY total_souls_helped ASC, created_at ASC
            """).execute(Tuple.of(userId))
            .map(rows -> {
                JsonArray arr = new JsonArray();
                for (Row row : rows) {
                    arr.add(new JsonObject()
                            .put("volunteerId", row.getString("id"))
                            .put("alias", row.getString("alias"))
                            .put("title", row.getString("alias")));
                }
                return arr;
            });
    }

    public Future<JsonObject> assignAvailableVolunteer(String userId) {
        return client.preparedQuery("""
                SELECT c.id AS conversation_id, cp2.user_id AS volunteer_id, COALESCE(u.alias, c.title) AS title
                FROM conversations c
                JOIN conversation_participants cp1 ON c.id = cp1.conversation_id
                JOIN conversation_participants cp2 ON c.id = cp2.conversation_id AND cp2.user_id != cp1.user_id
                JOIN users u ON cp2.user_id = u.id
                WHERE cp1.user_id = ? AND c.type = 'support' AND c.is_active = 1 AND u.role = 'volunteer'
                LIMIT 1
            """).execute(Tuple.of(userId))
            .compose(existingRows -> {
                if (existingRows.size() > 0) {
                    Row row = existingRows.iterator().next();
                    return Future.succeededFuture(new JsonObject()
                            .put("success", true)
                            .put("status", "connected")
                            .put("conversationId", row.getString("conversation_id"))
                            .put("title", row.getString("title"))
                            .put("userId", userId)
                            .put("volunteerId", row.getString("volunteer_id")));
                }

                return client.preparedQuery("""
                        SELECT id FROM users
                        WHERE role = 'volunteer' AND is_available = 1 AND id != ?
                        ORDER BY total_souls_helped ASC, created_at ASC
                        LIMIT 1
                    """).execute(Tuple.of(userId))
                    .compose(volunteerRows -> {
                        if (volunteerRows.size() == 0) {
                            return Future.failedFuture("No volunteer is available right now.");
                        }

                        String volunteerId = volunteerRows.iterator().next().getString("id");
                        return talkToVolunteer(volunteerId, userId);
                    });
            });
    }

    /**
     * UI Sidebar Indexer: Fetches all rooms starting with 'Conv_' that a user 
     * or volunteer is currently assigned to, dynamically pulling the OTHER person's 
     * real alias so names don't reset to user defaults on page refresh.
     */
    public Future<JsonArray> getUserConversations(String userId) {
        return client.preparedQuery("""
                SELECT 
                    c.id AS conversation_id, 
                    c.type AS conversation_type,
                    COALESCE(other_user.alias, c.title) AS computed_title,
                    other_user.role AS other_role
                FROM conversations c
                JOIN conversation_participants cp1 ON c.id = cp1.conversation_id
                -- Self-join the participant mapping layer to pull out the opposite chatter identity
                LEFT JOIN conversation_participants cp2 ON c.id = cp2.conversation_id AND cp2.user_id != cp1.user_id
                LEFT JOIN users other_user ON cp2.user_id = other_user.id
                WHERE cp1.user_id = ? AND c.id LIKE 'Conv_%'
                ORDER BY c.id DESC
            """).execute(Tuple.of(userId))
            .map(rows -> {
                JsonArray arr = new JsonArray();
                for (Row row : rows) {
                    arr.add(new JsonObject()
                        .put("conversationId", row.getString("conversation_id"))
                        .put("title", row.getString("computed_title"))
                        .put("type", row.getString("conversation_type"))
                        .put("role", row.getString("other_role") != null ? row.getString("other_role") : "member"));
                }
                return arr;
            });
    }

    /**
     * Backlog History Loader: Grabs past history message logs chronologically
     * along with sender aliases so the UI can print names above bubbles.
     */
    public Future<JsonArray> getConversationMessages(String conversationId) {
        return client.preparedQuery("""
                SELECT m.id, m.user_id, m.content, m.sent_at, COALESCE(u.alias, 'Anonymous') AS sender_alias
                FROM messages m
                LEFT JOIN users u ON m.user_id = u.id
                WHERE m.conversation_id = ? 
                ORDER BY m.sent_at ASC
            """).execute(Tuple.of(conversationId))
            .map(rows -> {
                JsonArray arr = new JsonArray();
                for (Row row : rows) {
                    arr.add(new JsonObject()
                        .put("id", row.getString("id"))
                        .put("userId", row.getString("user_id"))
                        .put("content", row.getString("content"))
                        .put("alias", row.getString("sender_alias"))
                        .put("sentAt", row.getLocalDateTime("sent_at") != null
                                ? row.getLocalDateTime("sent_at").toString()
                                : null));
                }
                return arr;
            });
    }

    /**
     * Offline Buffer Layer: Direct database fallback insertion route executed
     * when the WebSocket stream breaks or drops.
     */
    public Future<JsonObject> saveOfflineMessage(JsonObject msg) {
        String msgId = "Msg_" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        return client.preparedQuery("""
                INSERT INTO messages (id, conversation_id, user_id, content)
                VALUES (?, ?, ?, ?)
            """).execute(Tuple.of(
                msgId, 
                msg.getString("conversationId"), 
                msg.getString("userId"), 
                msg.getString("content")
            ))
            .map(v -> new JsonObject()
                    .put("status", "saved_offline")
                    .put("messageId", msgId)
                    .put("conversationId", msg.getString("conversationId"))
            );
    }
}
