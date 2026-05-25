package com.innerlink.innerlink_backend.chat.verticle;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.innerlink.innerlink_backend.chat.handler.ChatHandler;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;

public class ChatVerticle extends AbstractVerticle {

    private final ChatHandler chatHandler = new ChatHandler();


    private final Map<String, ServerWebSocket> userSockets = new ConcurrentHashMap<>();

    private final Map<String, Set<String>> conversationMembers = new ConcurrentHashMap<>();

    @Override
    public void start(Promise<Void> startPromise) {

        System.out.println("ChatVerticle started");

        vertx.createHttpServer()
                .webSocketHandler(this::handleWebSocket)
                .listen(8889)
                .onSuccess(server -> {
                    System.out.println("Chat WebSocket running on port " + server.actualPort());
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }

    private void handleWebSocket(ServerWebSocket ws) {

        if (!ws.path().equals("/ws/chat")) {
            ws.close((short) 1008, "Invalid WebSocket path");
            return;
        }

        ws.frameHandler(frame -> {
            if (!frame.isText()) return;

            JsonObject msg = new JsonObject(frame.textData());

            String type = msg.getString("type", "message");
            String userId = msg.getString("userId");
            String conversationId = msg.getString("conversationId");

            if (userId == null || conversationId == null) {
                ws.writeTextMessage(error("Missing userId or conversationId"));
                return;
            }

            userSockets.put(userId, ws);

       
            conversationMembers.computeIfAbsent(
                    conversationId,
                    k -> ConcurrentHashMap.newKeySet()
            );

            if ("join".equals(type)) {

                conversationMembers.get(conversationId).add(userId);

                ws.writeTextMessage(new JsonObject()
                        .put("type", "joined")
                        .put("conversationId", conversationId)
                        .encode());

                return;
            }

            if (msg.getString("content") == null) {
                ws.writeTextMessage(error("Missing message content"));
                return;
            }

            chatHandler.handleIncomingMessage(msg, vertx)
                    .onSuccess(result -> routeMessage(conversationId, result))
                    .onFailure(err -> ws.writeTextMessage(error(err.getMessage())));
        });

        ws.closeHandler(v -> {
            userSockets.values().remove(ws);
            System.out.println("WebSocket disconnected");
        });
    }

    private void routeMessage(String conversationId, JsonObject message) {

        Set<String> members = conversationMembers.get(conversationId);

        if (members == null || members.isEmpty()) return;

        JsonObject payload = new JsonObject()
                .put("type", "message")
                .put("conversationId", conversationId)
                .put("data", message);

        for (String userId : members) {

            ServerWebSocket socket = userSockets.get(userId);

            if (socket != null && !socket.isClosed()) {
                socket.writeTextMessage(payload.encode());
            }
        }
    }

 
    public void registerConversation(String conversationId, Set<String> userIds) {
        conversationMembers.putIfAbsent(conversationId, ConcurrentHashMap.newKeySet());
        conversationMembers.get(conversationId).addAll(userIds);
    }


    private String error(String msg) {
        return new JsonObject()
                .put("type", "error")
                .put("message", msg)
                .encode();
    }
}