package com.innerlink.innerlink_backend.chat.matching;

import com.innerlink.innerlink_backend.config.DatabaseConfig;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;

import java.time.LocalDate;
import java.util.UUID;

public class MoodMatchingService {

  private static String generateId(String prefix) {
    return prefix + "_" + LocalDate.now() + "_" +
      UUID.randomUUID().toString().substring(0, 9).toUpperCase();
  }

  public Future<JsonObject> findPeerMatch(String userId, String mood) {
    Promise<JsonObject> promise = Promise.promise();

    System.out.println("=== MATCHING DEBUG ===");
    System.out.println("User " + userId + " looking for peer with mood: " + mood);

    // Step 1: Check if user exists
    DatabaseConfig.getClient()
      .preparedQuery("SELECT id FROM users WHERE id = ?")
      .execute(Tuple.of(userId))
      .onSuccess(rows -> {
        if (rows.size() == 0) {
          System.out.println("User not found: " + userId);
          promise.fail("User not found");
          return;
        }
        System.out.println("User exists: " + userId);

        // Step 2: Check if user already has an active conversation
        DatabaseConfig.getClient()
          .preparedQuery("""
            SELECT c.id FROM conversations c
            JOIN conversation_participants cp ON c.id = cp.conversation_id
            WHERE cp.user_id = ? AND c.type = 'peer' AND c.is_active = 1
            LIMIT 1
          """)
          .execute(Tuple.of(userId))
          .onSuccess(existingRows -> {
            if (existingRows.size() > 0) {
              String existingId = existingRows.iterator().next().getString("id");
              System.out.println("User already has active conversation: " + existingId);
              promise.complete(new JsonObject()
                .put("conversationId", existingId)
                .put("existing", true)
                .put("status", "matched")
                .put("message", "Reusing existing conversation")
              );
              return;
            }
            System.out.println("User has no active conversation");

            // Step 3: Check if user is already in queue
            DatabaseConfig.getClient()
              .preparedQuery("SELECT user_id FROM mood_waiting_queue WHERE user_id = ?")
              .execute(Tuple.of(userId))
              .onSuccess(queueRows -> {
                if (queueRows.size() > 0) {
                  System.out.println("User is already in queue");
                  promise.complete(new JsonObject()
                    .put("peerId", null)
                    .put("status", "waiting")
                    .put("message", "Already waiting for a peer to join...")
                  );
                  return;
                }
                System.out.println("User not in queue");

                // Step 4: Look for a peer in the queue
                DatabaseConfig.getClient()
                  .preparedQuery("SELECT user_id FROM mood_waiting_queue WHERE mood = ? AND user_id != ? ORDER BY created_at ASC LIMIT 1")
                  .execute(Tuple.of(mood, userId))
                  .onSuccess(matchRows -> {
                    if (matchRows.size() == 0) {
                      System.out.println("No peer found in queue with mood: " + mood);
                      System.out.println("Adding user to queue...");

                      // NO PEER FOUND - Add user to queue
                      DatabaseConfig.getClient()
                        .preparedQuery("INSERT IGNORE  INTO mood_waiting_queue (user_id, mood) VALUES (?, ?)")
                        .execute(Tuple.of(userId, mood))
                        .onSuccess(v -> {
                          System.out.println("User added to queue. Waiting for peer...");
                          promise.complete(new JsonObject()
                            .put("peerId", null)
                            .put("status", "waiting")
                            .put("message", "Waiting for a peer to join...")
                          );
                        })
                        .onFailure(err -> {
                          System.err.println("Failed to add user to queue: " + err.getMessage());
                          promise.fail(err);
                        });
                    } else {
                      // PEER FOUND
                      String peerId = matchRows.iterator().next().getString("user_id");
                      System.out.println("Peer found in queue: " + peerId);

                      String conversationId = generateId("CONV");
                      System.out.println("Creating conversation: " + conversationId);

                      // Check if conversation already exists between these users
                      DatabaseConfig.getClient()
                        .preparedQuery("""
                          SELECT c.id FROM conversations c
                          JOIN conversation_participants cp1 ON c.id = cp1.conversation_id
                          JOIN conversation_participants cp2 ON c.id = cp2.conversation_id
                          WHERE cp1.user_id = ? AND cp2.user_id = ? AND c.type = 'peer' AND c.is_active = 1
                          LIMIT 1
                        """)
                        .execute(Tuple.of(userId, peerId))
                        .onSuccess(existingConvRows -> {
                          if (existingConvRows.size() > 0) {
                            String existingId = existingConvRows.iterator().next().getString("id");
                            System.out.println("Conversation already exists: " + existingId);

                            // Remove both from queue
                            DatabaseConfig.getClient()
                              .preparedQuery("DELETE FROM mood_waiting_queue WHERE user_id IN (?, ?)")
                              .execute(Tuple.of(userId, peerId))
                              .onSuccess(v -> {
                                System.out.println("Removed both from queue");
                                promise.complete(new JsonObject()
                                  .put("conversationId", existingId)
                                  .put("peerId", peerId)
                                  .put("status", "matched")
                                  .put("existing", true)
                                  .put("message", "Reusing existing conversation with peer")
                                );
                              })
                              .onFailure(promise::fail);
                          } else {
                            // Create new conversation
                            System.out.println("Creating new conversation...");

                            DatabaseConfig.getClient()
                              .preparedQuery("INSERT INTO conversations (id, type, title, is_active) VALUES (?, 'peer', ?, 1)")
                              .execute(Tuple.of(conversationId, "Peer Chat with " + peerId))
                              .onSuccess(v -> {
                                // Add participants
                                DatabaseConfig.getClient()
                                  .preparedQuery("INSERT INTO conversation_participants (conversation_id, user_id, role) VALUES (?, ?, 'member'), (?, ?, 'member')")
                                  .execute(Tuple.of(conversationId, userId, conversationId, peerId))
                                  .onSuccess(v2 -> {
                                    // Remove both from queue
                                    DatabaseConfig.getClient()
                                      .preparedQuery("DELETE FROM mood_waiting_queue WHERE user_id IN (?, ?)")
                                      .execute(Tuple.of(userId, peerId))
                                      .onSuccess(v3 -> {
                                        System.out.println("Conversation created successfully!");
                                        promise.complete(new JsonObject()
                                          .put("conversationId", conversationId)
                                          .put("peerId", peerId)
                                          .put("status", "matched")
                                          .put("message", "Peer found! You can now chat.")
                                        );
                                      })
                                      .onFailure(promise::fail);
                                  })
                                  .onFailure(promise::fail);
                              })
                              .onFailure(promise::fail);
                          }
                        })
                        .onFailure(promise::fail);
                    }
                  })
                  .onFailure(promise::fail);
              })
              .onFailure(promise::fail);
          })
          .onFailure(promise::fail);
      })
      .onFailure(promise::fail);

    return promise.future();
  }
}
