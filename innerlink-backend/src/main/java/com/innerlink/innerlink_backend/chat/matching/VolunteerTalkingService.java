package com.innerlink.innerlink_backend.chat.matching;

import com.innerlink.innerlink_backend.config.DatabaseConfig;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;

public class VolunteerTalkingService {

    private final SqlClient client = DatabaseConfig.getClient();

    private static String generateId(String prefix) {
        return prefix + "_" +
                java.time.LocalDate.now() + "_" +
                java.util.UUID.randomUUID().toString().substring(0, 9).toUpperCase();
    }

public Future<JsonObject> talkToVolunteer(String volunteerId, String userId) {

    String conversationId = generateId("Conv");

    return client.preparedQuery("""
            INSERT INTO conversations(id,type,title)
            VALUES(?,?,?)
        """).execute(Tuple.of(conversationId, "support", "Volunteer chat"))

        .compose(v -> client.preparedQuery("""
                INSERT INTO conversation_participants
                (conversation_id,user_id,role)
                VALUES(?,?,?),(?,?,?)
            """).execute(Tuple.of(
                    conversationId, userId, "member",
                    conversationId, volunteerId, "volunteer"
            )))

        .map(new JsonObject()
                .put("conversationId", conversationId)
                .put("userId", userId)
                .put("volunteerId", volunteerId));
}
}