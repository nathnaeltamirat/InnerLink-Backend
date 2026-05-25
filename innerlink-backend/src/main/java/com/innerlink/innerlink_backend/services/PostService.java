package com.innerlink.innerlink_backend.services;

import io.vertx.core.Vertx;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import com.innerlink.innerlink_backend.config.DatabaseConfig;

import java.util.UUID;

public class PostService {
  private final Vertx vertx;

  public PostService(Vertx vertx) {
    this.vertx = vertx;
  }

  public Future<JsonArray> getAllReflections() {
    Promise<JsonArray> promise = Promise.promise();

    DatabaseConfig.getClient()
      .preparedQuery("SELECT r.*, u.alias as user_alias FROM reflections r JOIN users u ON r.user_id = u.id ORDER BY r.created_at DESC")
      .execute()
      .onSuccess(rows -> {
        JsonArray result = new JsonArray();
        rows.forEach(row -> result.add(reflectionToJson(row)));
        promise.complete(result);
      })
      .onFailure(promise::fail);

    return promise.future();
  }

  public Future<JsonObject> createReflection(JsonObject data) {
    Promise<JsonObject> promise = Promise.promise();

    String id = UUID.randomUUID().toString();
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

  public Future<JsonObject> getReflectionById(String id) {
    Promise<JsonObject> promise = Promise.promise();

    DatabaseConfig.getClient()
      .preparedQuery("SELECT r.*, u.alias as user_alias FROM reflections r JOIN users u ON r.user_id = u.id WHERE r.id = ?")
      .execute(Tuple.of(id))
      .onSuccess(rows -> {
        if (rows.size() == 0) {
          promise.fail("Reflection not found");
          return;
        }
        Row row = rows.iterator().next();
        promise.complete(reflectionToJson(row));
      })
      .onFailure(promise::fail);

    return promise.future();
  }

  public Future<Void> deleteReflection(String id) {
    Promise<Void> promise = Promise.promise();

    DatabaseConfig.getClient()
      .preparedQuery("DELETE FROM reflections WHERE id = ?")
      .execute(Tuple.of(id))
      .onSuccess(v -> promise.complete())
      .onFailure(promise::fail);

    return promise.future();
  }

  private JsonObject reflectionToJson(Row row) {
    JsonObject json = new JsonObject();
    json.put("id", row.getString("id"));
    json.put("userId", row.getString("user_id"));
    json.put("userAlias", row.getString("user_alias"));
    json.put("content", row.getString("content"));
    json.put("imageUrl", row.getString("image_url"));
    json.put("postType", row.getString("post_type"));
    json.put("createdAt", row.getLocalDateTime("created_at") != null ?
      row.getLocalDateTime("created_at").toString() : new java.util.Date().toString());
    return json;
  }
}
