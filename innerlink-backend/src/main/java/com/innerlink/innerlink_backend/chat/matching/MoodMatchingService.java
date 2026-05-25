package com.innerlink.innerlink_backend.chat.matching;

import com.innerlink.innerlink_backend.config.DatabaseConfig;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;

public class MoodMatchingService {

    private final SqlClient client = DatabaseConfig.getClient();

    private static String generateId(String prefix) {
        return prefix + "_" +
                java.time.LocalDate.now() + "_" +
                java.util.UUID.randomUUID().toString().substring(0, 9).toUpperCase();
    }

    public Future<JsonObject> findPeerMatch(String userId, String mood) {

        return client.preparedQuery("""
                SELECT id FROM users
                WHERE id != ?
                AND current_mood = ?
                AND role = 'user'
                LIMIT 1
            """).execute(Tuple.of(userId, mood))

            .compose(rows -> {

                if (!rows.iterator().hasNext()) {
                    return Future.failedFuture("No peer match found");
                }

                String peerId = rows.iterator().next().getString("id");
                String conversationId = generateId("Conv");

                return client.preparedQuery("""
                        INSERT INTO conversations(id,type,title)
                        VALUES(?,?,?)
                    """).execute(Tuple.of(conversationId, "peer", "Mood Match"))

                    .compose(v -> client.preparedQuery("""
                            INSERT INTO conversation_participants
                            (conversation_id,user_id,role)
                            VALUES(?,?,?),(?,?,?)
                        """).execute(Tuple.of(
                                conversationId, userId, "member",
                                conversationId, peerId, "member"
                        )))

                    .map(v -> new JsonObject()
                            .put("conversationId", conversationId)
                            .put("peerId", peerId));
            });
    }
}