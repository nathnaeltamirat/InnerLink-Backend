package com.innerlink.innerlink_backend.config;

import io.vertx.core.Vertx;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;

import java.time.LocalDate;
import java.util.UUID;

import org.mindrot.jbcrypt.BCrypt;

public class DatabaseSetup {

    private static String generateId(String prefix) {
        return prefix + "_" +
                LocalDate.now() + "_" +
                UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    // USERS

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

    // REFLECTIONS

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

    //  CONVERSATIONS

    private static final String CREATE_CONVERSATIONS_TABLE = """
                CREATE TABLE IF NOT EXISTS conversations (
                    id            VARCHAR(36)  PRIMARY KEY,
                    type          ENUM('peer','group','support') NOT NULL,
                    title         VARCHAR(200),
                    is_active     TINYINT(1) DEFAULT 1,
                    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """;

    // PARTICIPANTS

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

    // MESSAGES

    private static final String CREATE_MESSAGES_TABLE = """
                CREATE TABLE IF NOT EXISTS messages (
                    id                VARCHAR(36) PRIMARY KEY,
                    conversation_id   VARCHAR(36) NOT NULL,
                    user_id           VARCHAR(36),
                    sender_type       ENUM('human','ai','system') DEFAULT 'human',
                    content           TEXT NOT NULL,
                    heaviness_score    INT DEFAULT 0,
                    condition_label    VARCHAR(50) DEFAULT 'neutral',
                    sent_at           DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
                )
            """;

    // EMERGENCY

    private static final String CREATE_EMERGENCY_FLAGS_TABLE = """
                CREATE TABLE IF NOT EXISTS emergency_flags (
                    id                VARCHAR(36) PRIMARY KEY,
                    user_id           VARCHAR(36) NOT NULL,
                    conversation_id   VARCHAR(36),
                    risk_level        ENUM('low','medium','high') NOT NULL,
                    flagged_content   TEXT NOT NULL,
                    status            ENUM('open','under_review','resolved') DEFAULT 'open',
                    flagged_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
                    resolved_at       DATETIME,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE SET NULL
                )
            """;

    // MATCHING QUEUE

    private static final String CREATE_MOOD_QUEUE_TABLE = """
                CREATE TABLE IF NOT EXISTS mood_waiting_queue (
                    user_id VARCHAR(36) PRIMARY KEY,
                    mood VARCHAR(50),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """;

    private static final String CREATE_INDEX_QUEUE = """
                CREATE INDEX idx_mood_queue ON mood_waiting_queue(mood, created_at)
            """;

    // DEFAULT DATA

    private static final String INSERT_DEFAULT_USERS = """
                INSERT INTO users (id, email, passkey_hash, alias, role, is_available)
                VALUES (?, ?, ?, ?, ?, ?)
            """;

    // SETUP

    public static Future<Void> setupDatabase(Vertx vertx, SqlClient client) {
        Promise<Void> promise = Promise.promise();

        System.out.println("🔧 Setting up database...");

        createTables(client)
                .compose(v -> insertDefaultData(client))
                .onSuccess(v -> {
                    System.out.println(" DB setup completed successfully");
                    promise.complete();
                })
                .onFailure(err -> {
                    System.err.println(" DB setup failed: " + err.getMessage());
                    promise.fail(err);
                });

        return promise.future();
    }


    private static Future<Void> createTables(SqlClient client) {
        return client.query(CREATE_USERS_TABLE).execute()

                .compose(v -> client.query(CREATE_REFLECTIONS_TABLE).execute())
                .compose(v -> client.query(CREATE_CONVERSATIONS_TABLE).execute())
                .compose(v -> client.query(CREATE_CONVERSATION_PARTICIPANTS_TABLE).execute())
                .compose(v -> client.query(CREATE_MESSAGES_TABLE).execute())
                .compose(v -> client.query(CREATE_EMERGENCY_FLAGS_TABLE).execute())


                .compose(v -> client.query(CREATE_MOOD_QUEUE_TABLE).execute())
                .compose(v -> ensureMessageModerationColumns(client))
                .compose(v -> ensureMoodQueueIndex(client))

                .onSuccess(v -> System.out.println(" All tables created"))
                .mapEmpty();
    }

    private static Future<Void> ensureMessageModerationColumns(SqlClient client) {
        return addColumnIfMissing(client, "messages", "heaviness_score", "INT DEFAULT 0")
                .compose(v -> addColumnIfMissing(client, "messages", "condition_label", "VARCHAR(50) DEFAULT 'neutral'"));
    }

    private static Future<Void> addColumnIfMissing(SqlClient client, String tableName, String columnName, String definition) {
        return client.preparedQuery("""
                        SELECT COUNT(*) AS count
                        FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                    """)
                .execute(Tuple.of(tableName, columnName))
                .compose(rows -> {
                    long count = rows.iterator().next().getLong("count");
                    if (count > 0) {
                        return Future.succeededFuture();
                    }
                    return client.query("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition)
                            .execute()
                            .mapEmpty();
                });
    }

    private static Future<Void> ensureMoodQueueIndex(SqlClient client) {
        return client.preparedQuery("""
                        SELECT COUNT(*) AS count
                        FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mood_waiting_queue' AND INDEX_NAME = 'idx_mood_queue'
                    """)
                .execute()
                .compose(rows -> {
                    long count = rows.iterator().next().getLong("count");
                    if (count > 0) {
                        return Future.succeededFuture();
                    }
                    return client.query(CREATE_INDEX_QUEUE).execute().mapEmpty();
                });
    }


    private static Future<Void> insertDefaultData(SqlClient client) {

        return client.preparedQuery("SELECT COUNT(*) as count FROM users WHERE email = ?")
                .execute(Tuple.of("admin@negeyachin.com"))

                .compose(rows -> {

                    long count = rows.iterator().next().getLong("count");

                    if (count > 0) {
                        System.out.println("ℹ Default users exist");
                        return Future.succeededFuture();
                    }

                    String adminId = generateId("ADMIN");
                    String volunteerId = generateId("VOL");
                    String userId = generateId("USER");

                    String pass1 = BCrypt.hashpw("admin123", BCrypt.gensalt());
                    String pass2 = BCrypt.hashpw("volunteer123", BCrypt.gensalt());
                    String pass3 = BCrypt.hashpw("user123", BCrypt.gensalt());

                    return client.preparedQuery(INSERT_DEFAULT_USERS)
                            .execute(Tuple.of(adminId, "admin@negeyachin.com", pass1, "Admin", "admin", 0))

                            .compose(v -> client.preparedQuery(INSERT_DEFAULT_USERS)
                                    .execute(Tuple.of(volunteerId, "volunteer@negeyachin.com", pass2, "Volunteer",
                                            "volunteer", 1)))

                            .compose(v -> client.preparedQuery(INSERT_DEFAULT_USERS)
                                    .execute(Tuple.of(userId, "user@negeyachin.com", pass3, "User", "user", 0)))

                            .mapEmpty();
                });
    }
}
