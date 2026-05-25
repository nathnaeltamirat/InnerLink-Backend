package com.innerlink.innerlink_backend.services;

import io.vertx.core.Vertx;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import com.innerlink.innerlink_backend.config.DatabaseConfig;

public class UserService {
  private final Vertx vertx;

  public UserService(Vertx vertx) {
    this.vertx = vertx;
  }

  public Future<JsonObject> getUserById(String userId) {
    Promise<JsonObject> promise = Promise.promise();

    DatabaseConfig.getClient()
      .preparedQuery("SELECT * FROM users WHERE id = ?")
      .execute(Tuple.of(userId))
      .onSuccess(rows -> {
        if (rows.size() == 0) {
          promise.fail("User not found");
          return;
        }
        Row row = rows.iterator().next();
        promise.complete(userToJson(row));
      })
      .onFailure(promise::fail);

    return promise.future();
  }

  public Future<JsonObject> updateUser(String userId, JsonObject data) {
    Promise<JsonObject> promise = Promise.promise();

    String alias = data.getString("alias");
    String mood = data.getString("currentMood");
    Boolean isAnonymous = data.getBoolean("isAnonymous");

    StringBuilder sql = new StringBuilder("UPDATE users SET ");
    StringBuilder params = new StringBuilder();
    int count = 0;

    if (alias != null) {
      sql.append("alias = ?");
      params.append(alias);
      count++;
    }
    if (mood != null) {
      if (count > 0) sql.append(", ");
      sql.append("current_mood = ?");
      params.append(", ").append(mood);
      count++;
    }
    if (isAnonymous != null) {
      if (count > 0) sql.append(", ");
      sql.append("is_anonymous = ?");
      params.append(", ").append(isAnonymous ? 1 : 0);
      count++;
    }
    sql.append(" WHERE id = ?");
    params.append(", ").append(userId);

    DatabaseConfig.getClient()
      .preparedQuery(sql.toString())
      .execute(Tuple.from(params.toString().split(", ")))
      .onSuccess(v -> {
        getUserById(userId)
          .onSuccess(user -> promise.complete(user))
          .onFailure(promise::fail);
      })
      .onFailure(promise::fail);

    return promise.future();
  }

  private JsonObject userToJson(Row row) {
    JsonObject json = new JsonObject();
    json.put("id", row.getString("id"));
    json.put("email", row.getString("email"));
    json.put("alias", row.getString("alias"));
    json.put("role", row.getString("role"));
    json.put("currentMood", row.getString("current_mood") != null ? row.getString("current_mood") : "Meditative");
    json.put("isAnonymous", row.getInteger("is_anonymous") == 1);
    json.put("isAvailable", row.getInteger("is_available") != null && row.getInteger("is_available") == 1);
    json.put("totalSoulsHelped", row.getInteger("total_souls_helped") != null ? row.getInteger("total_souls_helped") : 0);
    json.put("avatarUrl", row.getString("avatar_url"));
    return json;
  }
}
