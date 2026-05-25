package com.innerlink.innerlink_backend.config;

import io.vertx.core.Vertx;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;

import java.util.UUID;

import org.mindrot.jbcrypt.BCrypt;

public class DatabaseSetup {
  private static String generateId(String prefix) {
    return prefix + "_" +
        java.time.LocalDate.now() + "_" +
        java.util.UUID.randomUUID().toString().substring(0, 4).toUpperCase();
  }

  // 1. USERS TABLE
  private static final String CREATE_USERS_TABLE = """
          CREATE TABLE IF NOT EXISTS users (
              id               VARCHAR(36)  PRIMARY KEY,
              email            VARCHAR(255) NOT NULL UNIQUE,
              passkey_hash     VARCHAR(255) NOT NULL,
              alias            VARCHAR(100) NOT NULL DEFAULT 'Anonymous',
              avatar_url       TEXT,
              current_mood     VARCHAR(50)  DEFAULT 'Meditative',
              role             ENUM('user','volunteer','admin') NOT NULL DEFAULT 'user',
              is_anonymous     TINYINT(1)   DEFAULT 1,
              is_available     TINYINT(1)   DEFAULT 0,
              total_souls_helped INT        DEFAULT 0,
              created_at       DATETIME     DEFAULT CURRENT_TIMESTAMP
          )
      """;

  // 3. REFLECTIONS TABLE
  private static final String CREATE_REFLECTIONS_TABLE = """
          CREATE TABLE IF NOT EXISTS reflections (
              id            VARCHAR(36)  PRIMARY KEY,
              user_id       VARCHAR(36)  NOT NULL,
              content       TEXT         NOT NULL,
              image_url     TEXT,
              post_type     ENUM('reflection','letter','quote') DEFAULT 'reflection',
              created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
              FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
          )
      """;



  // 5. CONVERSATIONS TABLE
  private static final String CREATE_CONVERSATIONS_TABLE = """
          CREATE TABLE IF NOT EXISTS conversations (
              id            VARCHAR(36)  PRIMARY KEY,
              type         ENUM('peer','group','support')NOT NULL,
              title         VARCHAR(200),
              is_active     TINYINT(1)   DEFAULT 1,
              created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP
          )
      """;

  // 6. CONVERSATION PARTICIPANTS TABLE
  private static final String CREATE_CONVERSATION_PARTICIPANTS_TABLE = """
          CREATE TABLE IF NOT EXISTS conversation_participants (
              conversation_id VARCHAR(36),
              user_id         VARCHAR(36),
              role            ENUM('host','member','volunteer','seeker') DEFAULT 'member',
              joined_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
              PRIMARY KEY (conversation_id, user_id),
              FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
              FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
          )
      """;

  // 7. MESSAGES TABLE
  private static final String CREATE_MESSAGES_TABLE = """
          CREATE TABLE IF NOT EXISTS messages (
              id                VARCHAR(36)  PRIMARY KEY,
              conversation_id   VARCHAR(36) NOT NULL,
              user_id           VARCHAR(36),
              sender_type       ENUM('human','ai','system') DEFAULT 'human',
              content           TEXT         NOT NULL,
              detected_keywords TEXT,
              heaviness_score   INT DEFAULT 0,
              condition_label   VARCHAR(50),
              sent_at           DATETIME     DEFAULT CURRENT_TIMESTAMP,
              FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
              FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
          )
      """;

  // 8. EMERGENCY FLAGS TABLE
  private static final String CREATE_EMERGENCY_FLAGS_TABLE = """
          CREATE TABLE IF NOT EXISTS emergency_flags (
              id                VARCHAR(36)  PRIMARY KEY,
              user_id           VARCHAR(36)  NOT NULL,
              conversation_id   VARCHAR(36),
              risk_level        ENUM('low','medium','high') NOT NULL,
              flagged_content   TEXT         NOT NULL,
              status            ENUM('open','under_review','resolved') DEFAULT 'open',
              flagged_at        DATETIME     DEFAULT CURRENT_TIMESTAMP,
              resolved_at       DATETIME,
              FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
              FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE SET NULL
          )
      """;



  // DEFAULT DATA
  private static final String INSERT_DEFAULT_USERS = """
          INSERT IGNORE INTO users (id, email, passkey_hash, alias, role, is_available)
          VALUES (?, ?, ?, ?, ?, ?)
      """;

  private static final String INSERT_DEFAULT_REFLECTION = """
          INSERT IGNORE INTO reflections (id, user_id, content, post_type)
          VALUES (?, ?, ?, ?)
      """;

  public static Future<Void> setupDatabase(Vertx vertx, SqlClient client) {
    Promise<Void> promise = Promise.promise();

    System.out.println("🔧 Setting up database tables...");

    createTables(client)
        .compose(v -> insertDefaultData(client))
        .onSuccess(v -> {
          System.out.println("All 9 database tables created successfully!");
          promise.complete();
        })
        .onFailure(err -> {
          System.err.println(" Database setup failed: " + err.getMessage());
          promise.fail(err);
        });

    return promise.future();
  }

  private static Future<Void> createTables(SqlClient client) {
    Promise<Void> promise = Promise.promise();

    client.query(CREATE_USERS_TABLE).execute()
        .compose(v -> client.query(CREATE_REFLECTIONS_TABLE).execute())
        .compose(v -> client.query(CREATE_CONVERSATIONS_TABLE).execute())
        .compose(v -> client.query(CREATE_CONVERSATION_PARTICIPANTS_TABLE).execute())
        .compose(v -> client.query(CREATE_MESSAGES_TABLE).execute())
        .compose(v -> client.query(CREATE_EMERGENCY_FLAGS_TABLE).execute())
        .onSuccess(v -> {
          System.out.println("All 9 tables created successfully!");
          promise.complete();
        })
        .onFailure(err -> {
          System.err.println("Error creating tables: " + err.getMessage());
          promise.fail(err);
        });

    return promise.future();
  }

  private static Future<Void> insertDefaultData(SqlClient client) {
    Promise<Void> promise = Promise.promise();

    // First, check if data already exists
    client.preparedQuery("SELECT COUNT(*) as count FROM users WHERE email = ?")
        .execute(Tuple.of("admin@negeyachin.com"))
        .compose(rows -> {
          long count = rows.iterator().next().getLong("count");
          if (count > 0) {
            System.out.println("Default users already exist, skipping insertion");
            return Future.succeededFuture();
          }

          // Continue with insertion
          String adminId = generateId("ADMIN");
          String adminEmail = "admin@negeyachin.com";
          String adminPass = BCrypt.hashpw("admin123", BCrypt.gensalt());

          String volunteerId = generateId("VOL");
          String volunteerEmail = "volunteer@negeyachin.com";
          String volunteerPass = BCrypt.hashpw("volunteer123", BCrypt.gensalt());

          String userId = generateId("USER");
          String userEmail = "user@negeyachin.com";
          String userPass = BCrypt.hashpw("user123", BCrypt.gensalt());

          String reflectionId = generateId("REFLECTION");

          System.out.println("ADMIN ID => [" + adminId + "]");
          System.out.println("VOLUNTEER ID => [" + volunteerId + "]");
          System.out.println("USER ID => [" + userId + "]");

          // Explicit INSERT (not INSERT IGNORE) to see errors
          String insertUserSQL = """
                  INSERT INTO users (id, email, passkey_hash, alias, role, is_available)
                  VALUES (?, ?, ?, ?, ?, ?)
              """;

          return client.preparedQuery(insertUserSQL)
              .execute(Tuple.of(adminId, adminEmail, adminPass, "Admin", "admin", 0))
              .compose(v -> client.preparedQuery(insertUserSQL)
                  .execute(Tuple.of(volunteerId, volunteerEmail, volunteerPass, "Volunteer", "volunteer", 1)))
              .compose(v -> client.preparedQuery(insertUserSQL)
                  .execute(Tuple.of(userId, userEmail, userPass, "Silent Observer", "user", 0)))
              .compose(v -> {
                String insertReflectionSQL = """
                        INSERT INTO reflections (id, user_id, content, post_type)
                        VALUES (?, ?, ?, ?)
                    """;
                return client.preparedQuery(insertReflectionSQL)
                    .execute(Tuple.of(reflectionId, userId, "Welcome to Negeyachin. A space for digital stillness.",
                        "reflection"));
              });
        })
        .onSuccess(v -> {
          System.out.println("Default data created successfully!");
          System.out.println("   Admin: admin@negeyachin.com / admin123");
          System.out.println("   Volunteer: volunteer@negeyachin.com / volunteer123");
          System.out.println("   User: user@negeyachin.com / user123");
          promise.complete();
        })
        .onFailure(err -> {
          System.err.println("Error inserting default data: " + err.getMessage());
          err.printStackTrace();
          promise.fail(err);
        });

    return promise.future();
  }
}
