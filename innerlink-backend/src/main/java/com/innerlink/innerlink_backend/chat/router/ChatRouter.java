package com.innerlink.innerlink_backend.chat.router;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class ChatRouter {

    private final Vertx vertx;

    public ChatRouter(Vertx vertx) {
        this.vertx = vertx;
    }

    public void setupRoutes(Router router) {

        /* ================= DELEGATED VOLUNTEER EVENT ACTIONS ================= */

        // Initiates or fetches a workspace chat room matching a volunteer to a user's post/reflection
        router.post("/api/conversations/initiate").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null || body.getString("userId") == null || body.getString("volunteerId") == null) {
                sendBadRequest(ctx, "Both userId and volunteerId parameters are required to anchor a channel.");
                return;
            }
            forwardToEventBus(ctx, "chat.action.initiate_volunteer_room", body);
        });

        router.post("/api/conversations/assign-volunteer").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null || body.getString("userId") == null) {
                sendBadRequest(ctx, "userId is required to assign a volunteer channel.");
                return;
            }
            forwardToEventBus(ctx, "chat.action.assign_available_volunteer", body);
        });

        router.post("/chat/volunteer").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null || body.getString("userId") == null || body.getString("volunteerId") == null) {
                sendBadRequest(ctx, "userId and volunteerId required");
                return;
            }
            forwardToEventBus(ctx, "chat.route.volunteer", body);
        });
        
        router.post("/chat/mood").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null || body.getString("userId") == null || body.getString("mood") == null) {
                sendBadRequest(ctx, "userId and mood required");
                return;
            }
            forwardToEventBus(ctx, "chat.route.mood", body);
        });

        router.post("/chat/group").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null || body.getString("userId") == null || body.getString("mood") == null) {
                sendBadRequest(ctx, "userId and mood required");
                return;
            }
            forwardToEventBus(ctx, "chat.route.group", body);
        });

        /* ================= UNIFIED CHAT APP ENDPOINTS ================= */

        // 1. Fetch conversational list indexes for the sidebar panel layout
        router.get("/api/conversations/user/:userId").handler(ctx -> {
            String userId = ctx.pathParam("userId");
            JsonObject msgPayload = new JsonObject().put("userId", userId);
            
            forwardToEventBus(ctx, "chat.action.get_user_conversations", msgPayload);
        });

        router.get("/api/conversations/available-volunteers/:userId").handler(ctx -> {
            String userId = ctx.pathParam("userId");
            JsonObject msgPayload = new JsonObject().put("userId", userId);

            forwardToEventBus(ctx, "chat.action.get_available_volunteers", msgPayload);
        });

        // 2. Load historical messaging backlog elements securely
        router.get("/api/conversations/:conversationId/messages").handler(ctx -> {
            String conversationId = ctx.pathParam("conversationId");
            JsonObject msgPayload = new JsonObject().put("conversationId", conversationId);
            
            forwardToEventBus(ctx, "chat.action.get_room_messages", msgPayload);
        });

        // 3. Persistent injection fallback pipeline route when network channels alternate
        router.post("/api/conversations/messages/offline-save").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null || body.getString("conversationId") == null || body.getString("userId") == null || body.getString("content") == null) {
                sendBadRequest(ctx, "conversationId, userId, and content criteria fields are required.");
                return;
            }
            forwardToEventBus(ctx, "chat.action.persist_offline_msg", body);
        });
    }

    /* ================= TRANSLATION TRANSPORT CORE ================= */

    private void forwardToEventBus(RoutingContext ctx, String address, JsonObject body) {
        vertx.eventBus().<Object>request(address, body)
            .onSuccess(reply -> {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(reply.body().toString());
            })
            .onFailure(err -> {
                // GUARD: Detect empty exception string trackers inside Vert.x Service tasks
                String failureReason = err.getMessage();
                if (failureReason == null) {
                    failureReason = "Internal handling transaction failed inside database or messaging session state components.";
                }

                // Log trace onto backend server engine terminal for deep inspection
                System.err.println("❌ Vert.x EventBus communication failure at [" + address + "]: " + err.getClass().getName());
                err.printStackTrace();

                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(500)
                    .end(new JsonObject().put("error", failureReason).encode());
            });
    }

    private void sendBadRequest(RoutingContext ctx, String errorMsg) {
        ctx.response()
            .setStatusCode(400)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("error", errorMsg).encode());
    }
}
