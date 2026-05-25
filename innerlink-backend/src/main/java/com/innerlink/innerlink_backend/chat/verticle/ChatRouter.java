package com.innerlink.innerlink_backend.chat.verticle;



import com.innerlink.innerlink_backend.chat.service.ChatSessionService;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class ChatRouter {

    private final ChatSessionService chatSessionService;

    public ChatRouter(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    public void setupRoutes(Router router) {

 
        router.post("/chat/volunteer").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            String userId = body.getString("userId");
            String volunteerId = body.getString("volunteerId");
            if (userId == null || volunteerId == null) {
                ctx.response()
                        .setStatusCode(400)
                        .end(new JsonObject()
                                .put("error", "userId and volunteerId required")
                                .encode());
                return;
            }

            chatSessionService.startVolunteer(userId, volunteerId)
                    .onSuccess(session -> ctx.json(session))
                    .onFailure(err -> ctx.response()
                            .setStatusCode(500)
                            .end(new JsonObject()
                                    .put("error", err.getMessage())
                                    .encode()));
        });

        // =========================
        // MOOD CHAT
        // =========================
        router.post("/chat/mood").handler(ctx -> {

            JsonObject body = ctx.body().asJsonObject();

            String userId = body.getString("userId");
            String mood = body.getString("mood");

            if (userId == null || mood == null) {
                ctx.response()
                        .setStatusCode(400)
                        .end(new JsonObject()
                                .put("error", "userId and mood required")
                                .encode());
                return;
            }

            chatSessionService.startMood(userId, mood)
                    .onSuccess(session -> ctx.json(session))
                    .onFailure(err -> ctx.response()
                            .setStatusCode(500)
                            .end(new JsonObject()
                                    .put("error", err.getMessage())
                                    .encode()));
        });

        // =========================
        // GROUP CHAT
        // =========================
        router.post("/chat/group").handler(ctx -> {

            JsonObject body = ctx.body().asJsonObject();

            String userId = body.getString("userId");
            String mood = body.getString("mood");

            if (userId == null || mood == null) {
                ctx.response()
                        .setStatusCode(400)
                        .end(new JsonObject()
                                .put("error", "userId and mood required")
                                .encode());
                return;
            }

            chatSessionService.startGroup(userId, mood)
                    .onSuccess(session -> ctx.json(session))
                    .onFailure(err -> ctx.response()
                            .setStatusCode(500)
                            .end(new JsonObject()
                                    .put("error", err.getMessage())
                                    .encode()));
        });
    }
}