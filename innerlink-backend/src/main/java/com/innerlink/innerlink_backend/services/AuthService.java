package com.innerlink.innerlink_backend.services;

import io.vertx.core.Vertx;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import com.innerlink.innerlink_backend.config.DatabaseConfig;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.UUID;

public class AuthService {
  private final Vertx vertx;

  public AuthService(Vertx vertx) {
    this.vertx = vertx;
  }

  private String generateId() {
    return UUID.randomUUID().toString();
  }

  public Future<JsonObject> login(String email, String passkey) {
    Promise<JsonObject> promise = Promise.promise();

    DatabaseConfig.getClient()
      .preparedQuery("SELECT * FROM users WHERE email = ?")
      .execute(Tuple.of(email))
      .onSuccess(rows -> {
        if (rows.size() == 0) {
          promise.fail("User not found");
          return;
        }
        Row row = rows.iterator().next();
        if (!BCrypt.checkpw(passkey, row.getString("passkey_hash"))) {
          promise.fail("Invalid password");
          return;
        }
        promise.complete(createSession(row));
      })
      .onFailure(promise::fail);

    return promise.future();
  }

  public Future<JsonObject> register(JsonObject data) {
    Promise<JsonObject> promise = Promise.promise();
    String email = data.getString("email");
    String passkey = data.getString("passkey");
    String alias = data.getString("alias", "Anonymous");
    String role = data.getString("role", "user");

    DatabaseConfig.getClient()
      .preparedQuery("SELECT id FROM users WHERE email = ?")
      .execute(Tuple.of(email))
      .onSuccess(rows -> {
        if (rows.size() > 0) {
          promise.fail("Email already exists");
          return;
        }
        String id = generateId();
        String hash = BCrypt.hashpw(passkey, BCrypt.gensalt());
        DatabaseConfig.getClient()
          .preparedQuery("INSERT INTO users (id, email, passkey_hash, alias, role) VALUES (?, ?, ?, ?, ?)")
          .execute(Tuple.of(id, email, hash, alias, role))
          .onSuccess(v -> {
            JsonObject user = new JsonObject()
              .put("id", id)
              .put("email", email)
              .put("alias", alias)
              .put("role", role);
            promise.complete(user);
          })
          .onFailure(promise::fail);
      })
      .onFailure(promise::fail);

    return promise.future();
  }

  public Future<JsonObject> getCurrentUser(String token) {
    Promise<JsonObject> promise = Promise.promise();

    DatabaseConfig.getClient()
      .preparedQuery("SELECT u.* FROM users u JOIN sessions s ON u.id = s.user_id WHERE s.token = ?")
      .execute(Tuple.of(token))
      .onSuccess(rows -> {
        if (rows.size() == 0) {
          promise.fail("Invalid token");
          return;
        }
        Row row = rows.iterator().next();
        promise.complete(userToJson(row));
      })
      .onFailure(promise::fail);

    return promise.future();
  }

  private JsonObject createSession(Row user) {
    String token = UUID.randomUUID().toString();
    String sessionId = generateId();
    String expiresAt = LocalDateTime.now().plusDays(7).toString();

    DatabaseConfig.getClient()
      .preparedQuery("INSERT INTO sessions (id, user_id, token, expires_at) VALUES (?, ?, ?, ?)")
      .execute(Tuple.of(sessionId, user.getString("id"), token, expiresAt));

    return new JsonObject()
      .put("token", token)
      .put("user", userToJson(user));
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
