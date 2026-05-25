package com.innerlink.innerlink_backend.chat.service;

import java.util.UUID;
import com.innerlink.innerlink_backend.config.DatabaseConfig;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;

public class ChatService {
    
    private final SqlClient client = DatabaseConfig.getClient();
    
    private static String generateId(String prefix) {
        return prefix + "_" + java.time.LocalDate.now() + "_" + 
               UUID.randomUUID().toString().substring(0, 9).toUpperCase();
    }
    
    public Future<Void> saveMessage(JsonObject msg) {
        String id = msg.getString("id", generateId("ID"));
        return client.preparedQuery("""
            INSERT INTO messages (id, conversation_id, user_id, content, sender_type) 
            VALUES (?, ?, ?, ?, 'human')
        """).execute(Tuple.of(
            id,
            msg.getString("conversationId"),
            msg.getString("userId"),
            msg.getString("content")
        )).mapEmpty();
    }
    
    public Future<Void> updateMessageRisk(String messageId, Integer score, String label) {
        return client.preparedQuery("""
            UPDATE messages 
            SET heaviness_score = ?, condition_label = ? 
            WHERE id = ?
        """).execute(Tuple.of(score, label, messageId)).mapEmpty();
    }
    
    public Future<Void> flagEmergency(JsonObject msg, JsonObject analysis, Vertx vertx) {
        String flagId = generateId("FLAG");
        String userId = msg.getString("userId");
        String conversationId = msg.getString("conversationId");
        
        return client.preparedQuery("""
            INSERT INTO emergency_flags (id, user_id, conversation_id, risk_level, flagged_content) 
            VALUES (?, ?, ?, 'high', ?)
        """).execute(Tuple.of(
            flagId,
            userId,
            conversationId,
            msg.getString("content")
        )).compose(v -> updateUserRisk(userId)).mapEmpty();
    }
    
    public Future<Void> updateUserRisk(String userId) {
        return client.preparedQuery("""
            UPDATE users 
            SET current_mood = 'Crisis' 
            WHERE id = ?
        """).execute(Tuple.of(userId)).mapEmpty();
    }
    
    public Future<JsonObject> getMessages(String conversationId) {
        Promise<JsonObject> promise = Promise.promise();
        
        client.preparedQuery("""
            SELECT * FROM messages 
            WHERE conversation_id = ? 
            ORDER BY sent_at ASC
        """).execute(Tuple.of(conversationId)).onSuccess(rows -> {
            JsonObject result = new JsonObject();
            rows.forEach(row -> {
                result.put(
                    row.getString("id"),
                    new JsonObject()
                        .put("content", row.getString("content"))
                        .put("userId", row.getString("user_id"))
                        .put("score", row.getInteger("heaviness_score"))
                        .put("label", row.getString("condition_label"))
                        .put("time", row.getLocalDateTime("sent_at").toString())
                );
            });
            promise.complete(result);
        }).onFailure(err -> promise.fail(err));
        
        return promise.future();
    }
}