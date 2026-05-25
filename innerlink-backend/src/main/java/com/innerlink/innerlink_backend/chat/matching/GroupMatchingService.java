package com.innerlink.innerlink_backend.chat.matching;

import com.innerlink.innerlink_backend.config.DatabaseConfig;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;

public class GroupMatchingService {

    private final SqlClient client = DatabaseConfig.getClient();

    private static String generateId(String prefix) {
        return prefix + "_" +
                java.time.LocalDate.now() + "_" +
                java.util.UUID.randomUUID().toString().substring(0, 9).toUpperCase();
    }

    public Future<JsonObject> joinMoodGroup(String userId, String mood) {

        return client.preparedQuery("""
                SELECT id FROM conversations
                WHERE type = 'group' AND title = ?
                LIMIT 1
            """).execute(Tuple.of(mood))

            .compose(rows -> {

                if (rows.iterator().hasNext()) {
                    String conversationId = rows.iterator().next().getString("id");

                    return addParticipant(conversationId, userId)
                            .map(v -> new JsonObject()
                                    .put("conversationId", conversationId)
                                    .put("group", mood));
                }

                return createMoodGroup(userId, mood);
            });
    }

    private Future<Void> addParticipant(String conversationId, String userId) {
        return client.preparedQuery("""
                INSERT INTO conversation_participants
                (conversation_id,user_id,role)
                VALUES(?,?,?)
            """).execute(Tuple.of(conversationId, userId, "member"))
            .mapEmpty();
    }

    private Future<JsonObject> createMoodGroup(String userId, String mood) {

        String conversationId = generateId("Group");

        return client.preparedQuery("""
                INSERT INTO conversations(id,type,title)
                VALUES(?,?,?)
            """).execute(Tuple.of(conversationId, "group", mood))

            .compose(v -> addParticipant(conversationId, userId))

            .map(new JsonObject()
                    .put("conversationId", conversationId)
                    .put("group", mood)
                    .put("type", "group")
                    .put("message", "Created mood group"));
    }
}