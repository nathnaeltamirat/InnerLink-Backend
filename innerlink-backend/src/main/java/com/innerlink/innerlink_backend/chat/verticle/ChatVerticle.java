package com.innerlink.innerlink_backend.chat.verticle;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.innerlink.innerlink_backend.chat.handler.ChatHandler;
import com.innerlink.innerlink_backend.chat.matching.VolunteerTalkingService;
import com.innerlink.innerlink_backend.chat.service.ChatSessionService;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ChatVerticle extends AbstractVerticle {

    private final ChatHandler chatHandler = new ChatHandler();
    private ChatSessionService chatSessionService;
    private VolunteerTalkingService volunteerTalkingService;

    private final Map<String, ServerWebSocket> userSockets = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> conversationMembers = new ConcurrentHashMap<>();

    @Override
    public void start(Promise<Void> startPromise) {

        this.chatSessionService = new ChatSessionService(this);
        this.volunteerTalkingService = new VolunteerTalkingService();

        System.out.println("ChatVerticle scaling up with unified messenger support...");

        // ---------------- MOOD ----------------
        vertx.eventBus().<JsonObject>consumer("chat.route.mood", message -> {
            JsonObject body = message.body();

            chatSessionService.startMood(
                    body.getString("userId"),
                    body.getString("mood")).onSuccess(res -> {
                        String conversationId = res.getString("conversationId");
                        String peerId = res.getString("peerId");

                        if (conversationId == null) {
                            message.reply(res);
                            return;
                        }

                        if (peerId != null) {
                            registerConversation(conversationId, Set.of(body.getString("userId"), peerId));
                        } else {
                            registerConversation(conversationId, Set.of(body.getString("userId")));
                        }
                        message.reply(res);
                    }).onFailure(err -> message.fail(500, err.getMessage()));
        });

        // ---------------- GROUP ----------------
        vertx.eventBus().<JsonObject>consumer("chat.route.group", message -> {
            JsonObject body = message.body();

            chatSessionService.startGroup(
                    body.getString("userId"),
                    body.getString("mood")).onSuccess(res -> {
                        registerConversation(
                                res.getString("conversationId"),
                                Set.of(body.getString("userId")));
                        message.reply(res);
                    }).onFailure(err -> message.fail(500, err.getMessage()));
        });

        // ---------------- VOLUNTEER ----------------
        vertx.eventBus().<JsonObject>consumer("chat.route.volunteer", message -> {
            JsonObject body = message.body();

            chatSessionService.startVolunteer(
                    body.getString("userId"),
                    body.getString("volunteerId")).onSuccess(res -> {
                        registerConversation(
                                res.getString("conversationId"),
                                Set.of(body.getString("userId"), body.getString("volunteerId")));
                        message.reply(res);
                    }).onFailure(err -> message.fail(500, err.getMessage()));
        });

        // ---------------- UNIFIED MESSENGER EVENT BUS CONSUMERS ----------------

        // NEW - Endpoint integration D: Handles creation/retrieval of rooms from posts
        // or reflections
        vertx.eventBus().<JsonObject>consumer("chat.action.initiate_volunteer_room", message -> {
            JsonObject body = message.body();
            String userId = body.getString("userId");
            String volunteerId = body.getString("volunteerId");

            // Direct mapping to your existing session initialization logic
            chatSessionService.startVolunteer(userId, volunteerId)
                    .onSuccess(res -> {
                        String conversationId = res.getString("conversationId");

                        // Map the internal real-time memory sets instantly
                        registerConversation(conversationId, Set.of(userId, volunteerId));

                        // Return the data layer response payload context smoothly
                        message.reply(res);
                    })
                    .onFailure(err -> {

                        System.err.println("❌ Critical failure inside chatSessionService.startVolunteer:");
                        err.printStackTrace();
                        message.fail(500,
                                err.getMessage() != null ? err.getMessage() : "Unknown session allocation error.");
                    });
        });

        vertx.eventBus().<JsonObject>consumer("chat.action.assign_available_volunteer", message -> {
            String userId = message.body().getString("userId");

            volunteerTalkingService.assignAvailableVolunteer(userId)
                    .onSuccess(res -> {
                        registerConversation(
                                res.getString("conversationId"),
                                Set.of(userId, res.getString("volunteerId")));
                        message.reply(res);
                    })
                    .onFailure(err -> message.fail(404, err.getMessage()));
        });

        // Endpoint integration A: Fetches left sidebar channel history items
        vertx.eventBus().<JsonObject>consumer("chat.action.get_user_conversations", message -> {
            String userId = message.body().getString("userId");

            volunteerTalkingService.getUserConversations(userId)
                    .onSuccess(message::reply)
                    .onFailure(err -> {
                        // Log trace for debugging visibility
                        System.out.println(
                                "⚠️ Sidebar database lookup completed for user (" + userId + "): " + err.getMessage());
                        // Fallback to empty list layout instead of throwing a 500 error chain block
                        message.reply(new JsonArray());
                    });
        });

        // Endpoint integration B: Fetches sequence bubble logs when clicking a thread
        vertx.eventBus().<JsonObject>consumer("chat.action.get_room_messages", message -> {
            String conversationId = message.body().getString("conversationId");

            volunteerTalkingService.getConversationMessages(conversationId)
                    .onSuccess(message::reply)
                    .onFailure(err -> {
                        System.out.println("⚠️ Message log fetch completed for room (" + conversationId + "): "
                                + err.getMessage());
                        message.reply(new JsonArray());
                    });
        });

        // Endpoint integration C: Safe HTTP fallback routing wrapper when WebSockets
        // drop
        vertx.eventBus().<JsonObject>consumer("chat.action.persist_offline_msg", message -> {
            JsonObject body = message.body();

            volunteerTalkingService.saveOfflineMessage(body)
                    .onSuccess(res -> {
                        routeMessage(body.getString("conversationId"), body);
                        message.reply(res);
                    })
                    .onFailure(err -> message.fail(500, err.getMessage()));
        });

        // ---------------- WS SERVER ----------------
        vertx.createHttpServer()
                .webSocketHandler(this::handleWebSocket)
                .listen(8889)
                .onSuccess(server -> {
                    System.out.println("Chat WebSocket running on " + server.actualPort());
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }

    // ================= WEB SOCKET =================

    private void handleWebSocket(ServerWebSocket ws) {

        if (!ws.path().equals("/ws/chat")) {
            ws.close((short) 1008, "Invalid WebSocket path");
            return;
        }

        ws.frameHandler(frame -> {
            if (!frame.isText())
                return;

            JsonObject msg = new JsonObject(frame.textData());

            String type = msg.getString("type", "message");
            String userId = msg.getString("userId");
            String conversationId = msg.getString("conversationId");
            String content = msg.getString("content");

            String username = msg.getString("username", "Anonymous");
            String alias = msg.getString("alias", username);

            if (userId == null || conversationId == null) {
                ws.writeTextMessage(error("Missing userId or conversationId"));
                return;
            }

            userSockets.put(userId, ws);
            conversationMembers.computeIfAbsent(
                    conversationId,
                    k -> ConcurrentHashMap.<String>newKeySet());

            // JOIN EVENT: Triggered dynamically by client selecting older threads
            if ("join".equals(type)) {
                conversationMembers.get(conversationId).add(userId);

                ws.writeTextMessage(new JsonObject()
                        .put("type", "joined")
                        .put("conversationId", conversationId)
                        .encode());
                return;
            }

            if ("typing".equals(type) || "presence".equals(type)) {
                conversationMembers.get(conversationId).add(userId);
                routeEvent(conversationId, msg);
                return;
            }

            if (content == null) {
                ws.writeTextMessage(error("Missing message content"));
                return;
            }

            chatHandler.handleIncomingMessage(msg, vertx)
                    .onSuccess(result -> {
                        JsonObject data = result.getJsonObject("data", result);
                        JsonObject standardizedPayload = new JsonObject()
                                .put("userId", userId)
                                .put("conversationId", conversationId)
                                .put("content",
                                        data.getString("content") != null ? data.getString("content") : content)
                                .put("username",
                                        data.getString("username") != null ? data.getString("username") : username)
                                .put("alias", data.getString("alias") != null ? data.getString("alias") : alias)
                                .put("timestamp", msg.getString("timestamp"))
                                .put("risk", data.getString("risk"))
                                .put("score", data.getInteger("score"));

                        routeMessage(conversationId, standardizedPayload);
                    })
                    .onFailure(err -> ws.writeTextMessage(error(err.getMessage())));
        });

        ws.closeHandler(v -> {
            userSockets.values().remove(ws);
            conversationMembers.values()
                    .forEach(set -> set.removeIf(id -> userSockets.get(id) == ws));
        });
    }

    // ================= ROUTING =================

    private void routeMessage(String conversationId, JsonObject message) {

        Set<String> members = conversationMembers.get(conversationId);
        if (members == null || members.isEmpty())
            return;

        JsonObject payload = new JsonObject()
                .put("type", "message")
                .put("conversationId", conversationId)
                .put("data", message);

        String encoded = payload.encode();

        for (String userId : members) {
            ServerWebSocket socket = userSockets.get(userId);
            if (socket != null && !socket.isClosed()) {
                socket.writeTextMessage(encoded);
            }
        }
    }

    private void routeEvent(String conversationId, JsonObject event) {

        Set<String> members = conversationMembers.get(conversationId);
        if (members == null || members.isEmpty())
            return;

        String encoded = event.encode();

        for (String memberId : members) {
            if (memberId.equals(event.getString("userId"))) {
                continue;
            }
            ServerWebSocket socket = userSockets.get(memberId);
            if (socket != null && !socket.isClosed()) {
                socket.writeTextMessage(encoded);
            }
        }
    }

    // ================= API =================

    public void registerConversation(String conversationId, Set<String> userIds) {
        conversationMembers.computeIfAbsent(
                conversationId,
                k -> ConcurrentHashMap.<String>newKeySet());
        conversationMembers.get(conversationId).addAll(userIds);
    }

    private String error(String msg) {
        return new JsonObject()
                .put("type", "error")
                .put("message", msg)
                .encode();
    }
}
