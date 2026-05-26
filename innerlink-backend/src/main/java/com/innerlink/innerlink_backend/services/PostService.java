package com.innerlink.innerlink_backend.services;

import io.vertx.core.Vertx;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.sqlclient.Tuple;

import java.util.UUID;

import com.innerlink.innerlink_backend.config.DatabaseConfig;

public class PostService {
  private final Vertx vertx;

  public PostService(Vertx vertx) {
    this.vertx = vertx;
  }

  private static String generateId(String prefix) {
    return prefix + "_" + java.time.LocalDate.now() + "_" +
        UUID.randomUUID().toString().substring(0, 9).toUpperCase();
  }

  public Future<JsonArray> getAllReflections() {
    Promise<JsonArray> promise = Promise.promise();

    DatabaseConfig.getClient()
        .preparedQuery(
            "SELECT r.*, u.alias as user_alias FROM reflections r JOIN users u ON r.user_id = u.id ORDER BY r.created_at DESC")
        .execute()
        .onSuccess(rows -> {
          JsonArray result = new JsonArray();
          rows.forEach(row -> {
            JsonObject obj = new JsonObject();
            obj.put("id", row.getString("id"));
            obj.put("userId", row.getString("user_id"));
            obj.put("userAlias", row.getString("user_alias"));
            obj.put("content", row.getString("content"));
            obj.put("imageUrl", row.getString("image_url"));
            obj.put("postType", row.getString("post_type"));
            obj.put("createdAt", row.getLocalDateTime("created_at").toString());
            result.add(obj);
          });
          promise.complete(result);
        })
        .onFailure(promise::fail);

    return promise.future();
  }

  public Future<JsonObject> createReflection(JsonObject data) {
    Promise<JsonObject> promise = Promise.promise();

    String id = generateId("REF");
    String userId = data.getString("userId");
    String content = data.getString("content");
    String imageUrl = data.getString("imageUrl");
    String postType = data.getString("postType", "reflection");

    DatabaseConfig.getClient()
        .preparedQuery("INSERT INTO reflections (id, user_id, content, image_url, post_type) VALUES (?, ?, ?, ?, ?)")
        .execute(Tuple.of(id, userId, content, imageUrl, postType))
        .onSuccess(v -> {
          JsonObject reflection = new JsonObject()
              .put("id", id)
              .put("userId", userId)
              .put("content", content)
              .put("imageUrl", imageUrl)
              .put("postType", postType)
              .put("createdAt", new java.util.Date().toString());
          promise.complete(reflection);
        })
        .onFailure(promise::fail);

    return promise.future();
  }
}
