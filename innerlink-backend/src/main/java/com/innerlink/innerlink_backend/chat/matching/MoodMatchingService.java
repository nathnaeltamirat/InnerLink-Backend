package com.innerlink.innerlink_backend.chat.matching;

import com.innerlink.innerlink_backend.config.DatabaseConfig;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.*;

import java.util.*;
import java.util.concurrent.*;

public class MoodMatchingService {

    private final SqlClient client = DatabaseConfig.getClient();

    // 🔥 in-memory fallback queue
    private static final Map<String, Queue<String>> WAITING = new ConcurrentHashMap<>();

    private static String generateId() {
        return "Conv_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public Future<JsonObject> findPeerMatch(String userId, String mood) {

        String m = mood == null ? "calm" : mood.toLowerCase();

        // =========================
        // 1. TRY DB MATCH FIRST
        // =========================
        return client.preparedQuery("""
            SELECT id FROM users
            WHERE id != ?
            AND role = 'user'
            AND current_mood IS NOT NULL
            AND LOWER(current_mood) = LOWER(?)
            LIMIT 1
        """)
        .execute(Tuple.of(userId, m))

        .compose(rows -> {

            if (rows.iterator().hasNext()) {

                String peerId = rows.iterator().next().getString("id");
                String conversationId = generateId();

                return Future.succeededFuture(
                        new JsonObject()
                                .put("status", "matched")
                                .put("conversationId", conversationId)
                                .put("peerId", peerId)
                );
            }

            // =========================
            // 2. FALLBACK QUEUE MATCH
            // =========================
            WAITING.putIfAbsent(m, new ConcurrentLinkedQueue<>());
            Queue<String> queue = WAITING.get(m);

            String peer = queue.poll();

            if (peer != null && !peer.equals(userId)) {

                String conversationId = generateId();

                return Future.succeededFuture(
                        new JsonObject()
                                .put("status", "matched")
                                .put("conversationId", conversationId)
                                .put("peerId", peer)
                );
            }

            // =========================
            // 3. PUT INTO QUEUE
            // =========================
            queue.add(userId);

            return Future.succeededFuture(
                    new JsonObject()
                            .put("status", "waiting")
                            .put("message", "Waiting for peer match")
            );
        })

        // =========================
        // 4. NEVER CRASH API
        // =========================
        .recover(err -> {
            err.printStackTrace();
            return Future.succeededFuture(
                    new JsonObject()
                            .put("status", "error")
                            .put("message", "Match service error")
            );
        });
    }
}