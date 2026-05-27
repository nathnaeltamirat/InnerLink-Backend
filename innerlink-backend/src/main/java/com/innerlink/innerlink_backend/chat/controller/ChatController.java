package com.innerlink.innerlink_backend.chat.controller;

import com.innerlink.innerlink_backend.chat.matching.VolunteerTalkingService;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class ChatController {

    private final VolunteerTalkingService chatService;

    public ChatController(VolunteerTalkingService chatService) {
        this.chatService = chatService;
    }

    public void handleGetConversations(RoutingContext ctx) {
        String userId = ctx.pathParam("userId");
        
        chatService.getUserConversations(userId)
            .onSuccess(jsonArray -> sendJson(ctx, 200, jsonArray.encode()))
            .onFailure(err -> sendError(ctx, 500, err.getMessage()));
    }

    public void handleGetMessages(RoutingContext ctx) {
        String conversationId = ctx.pathParam("conversationId");

        chatService.getConversationMessages(conversationId)
            .onSuccess(jsonArray -> sendJson(ctx, 200, jsonArray.encode()))
            .onFailure(err -> sendError(ctx, 500, err.getMessage()));
    }

    public void handleSaveOfflineMessage(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();

        if (body == null || !body.containsKey("conversationId") || !body.containsKey("userId") || !body.containsKey("content")) {
            sendError(ctx, 400, "Invalid payload context. Missing required fields.");
            return;
        }

        chatService.saveOfflineMessage(body)
            .onSuccess(res -> sendJson(ctx, 201, res.encode()))
            .onFailure(err -> sendError(ctx, 500, err.getMessage()));
    }

   
    private void sendJson(RoutingContext ctx, int statusCode, String jsonString) {
        ctx.response()
            .putHeader("content-type", "application/json")
            .setStatusCode(statusCode)
            .end(jsonString);
    }

    private void sendError(RoutingContext ctx, int statusCode, String message) {
        ctx.response()
            .putHeader("content-type", "application/json")
            .setStatusCode(statusCode)
            .end(new JsonObject().put("error", message).encode());
    }
}