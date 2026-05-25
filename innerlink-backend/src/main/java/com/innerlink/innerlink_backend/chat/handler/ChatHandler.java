package com.innerlink.innerlink_backend.chat.handler;

import com.innerlink.innerlink_backend.chat.ai.AIModerationService;
import com.innerlink.innerlink_backend.chat.service.ChatService;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class ChatHandler {

    private final ChatService chatService = new ChatService();
    private final AIModerationService aiService = new AIModerationService();

    public Future<JsonObject> handleIncomingMessage(JsonObject msg, Vertx vertx) {

        if (!msg.containsKey("userId") ||
            !msg.containsKey("conversationId") ||
            !msg.containsKey("content")) {
            return Future.failedFuture("Missing required fields");
        }

        String messageId = generateId("Message");
        msg.put("id", messageId);

        return chatService.saveMessage(msg)
                .compose(saved -> aiService.analyzeMessage(msg, vertx))
                .compose(analysis -> {

                    int score = analysis.getInteger("heaviness_score");
                    String label = analysis.getString("condition_label");
                    boolean danger = analysis.getBoolean("is_danger");

                    return chatService.updateMessageRisk(messageId, score, label)
                            .compose(v -> {

                                JsonObject response = new JsonObject()
                                        .put("status", "ok")
                                        .put("messageId", messageId)
                                        .put("risk", label)
                                        .put("score", score);

                                if (danger) {
                                    chatService.flagEmergency(msg, analysis, vertx);
                                    response.put("alert", "emergency_triggered");
                                }

                                return Future.succeededFuture(response);
                            });
                });
    }

    private static String generateId(String prefix) {
        return prefix + "_" +
                java.time.LocalDate.now() + "_" +
                java.util.UUID.randomUUID().toString().substring(0, 9).toUpperCase();
    }
}