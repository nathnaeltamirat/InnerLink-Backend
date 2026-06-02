package com.innerlink.innerlink_backend.config;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;

public class DatabaseConfig {
  private static SqlClient client;
  private static boolean isSetupComplete = false;

  private static final String DB_PASSWORD = "267226";
  private static final String DB_USER = "";
  private static final String DB_NAME = "innerlink";

  public static Future<Void> init(Vertx vertx) {
    Promise<Void> promise = Promise.promise();
    System.out.println("🔌 Connecting to MySQL...");

    JDBCConnectOptions connectOptions = new JDBCConnectOptions()
        .setJdbcUrl(
            "jdbc:mysql://localhost:3306/mysql?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true")
        .setUser(DB_USER)
        .setPassword(DB_PASSWORD);

    PoolOptions poolOptions = new PoolOptions().setMaxSize(1);
    SqlClient tempClient = JDBCPool.pool(vertx, connectOptions, poolOptions);

    tempClient.query("CREATE DATABASE IF NOT EXISTS " + DB_NAME)
        .execute()
        .compose(v -> {
          System.out.println("Database '" + DB_NAME + "' created or already exists");
          tempClient.close();
          // 2. Return the next async operation chain
          return connectToNegeyachin(vertx);
        })
        .onSuccess(v -> promise.complete())
        .onFailure(err -> {
          System.err.println("Failed to initialize database: " + err.getMessage());
          tempClient.close();
          promise.fail(err);
        });

    return promise.future();
  }

  private static Future<Void> connectToNegeyachin(Vertx vertx) {
    Promise<Void> promise = Promise.promise();

    JDBCConnectOptions connectOptions = new JDBCConnectOptions()
        .setJdbcUrl("jdbc:mysql://localhost:3306/" + DB_NAME + "?useSSL=false&serverTimezone=UTC")
        .setUser(DB_USER)
        .setPassword(DB_PASSWORD);

    PoolOptions poolOptions = new PoolOptions().setMaxSize(10);
    client = JDBCPool.pool(vertx, connectOptions, poolOptions);

    System.out.println("Connected to '" + DB_NAME + "' database!");

    if (!isSetupComplete) {
      DatabaseSetup.setupDatabase(vertx, client)
          .onSuccess(v -> {
            isSetupComplete = true;
            System.out.println("Database schema ready!");
            promise.complete();
          })
          .onFailure(err -> {
            System.err.println("Database setup warning: " + err.getMessage());

            promise.complete();
          });
    } else {
      promise.complete();
    }

    return promise.future();
  }

  public static SqlClient getClient() {
    if (client == null) {
      throw new IllegalStateException("Database client pool accessed before complete async initialization!");
    }
    return client;
  }
}
