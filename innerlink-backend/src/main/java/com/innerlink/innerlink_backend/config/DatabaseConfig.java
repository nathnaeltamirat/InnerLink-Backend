package com.innerlink.innerlink_backend.config;

import io.vertx.core.Vertx;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;

public class DatabaseConfig {
  private static SqlClient client;
  private static boolean isSetupComplete = false;

  private static final String DB_PASSWORD = ""; 
  private static final String DB_USER = "root";
  private static final String DB_NAME = "innerlink";

  public static void init(Vertx vertx) {
    System.out.println("🔌 Connecting to MySQL...");

    JDBCConnectOptions connectOptions = new JDBCConnectOptions()
      .setJdbcUrl("jdbc:mysql://localhost:3306/mysql?useSSL=false&serverTimezone=UTC")
      .setUser(DB_USER)
      .setPassword(DB_PASSWORD);

    PoolOptions poolOptions = new PoolOptions()
      .setMaxSize(1);

    SqlClient tempClient = JDBCPool.pool(vertx, connectOptions, poolOptions);

    tempClient.query("CREATE DATABASE IF NOT EXISTS " + DB_NAME)
      .execute()
      .onSuccess(v -> {
        System.out.println("Database '" + DB_NAME + "' created or already exists");
        tempClient.close();

        connectToNegeyachin(vertx);
      })
      .onFailure(err -> {
        System.err.println("Failed to create database: " + err.getMessage());
        tempClient.close();
      });
  }

  private static void connectToNegeyachin(Vertx vertx) {
    JDBCConnectOptions connectOptions = new JDBCConnectOptions()
      .setJdbcUrl("jdbc:mysql://localhost:3306/" + DB_NAME + "?useSSL=false&serverTimezone=UTC")
      .setUser(DB_USER)
      .setPassword(DB_PASSWORD);

    PoolOptions poolOptions = new PoolOptions()
      .setMaxSize(10);

    client = JDBCPool.pool(vertx, connectOptions, poolOptions);

    System.out.println("Connected to '" + DB_NAME + "' database!");

    if (!isSetupComplete) {
      DatabaseSetup.setupDatabase(vertx, client)
        .onSuccess(v -> {
          isSetupComplete = true;
          System.out.println("Database schema ready!");
        })
        .onFailure(err -> {
          System.err.println(" Database setup warning: " + err.getMessage());
        });
    }
  }

  public static SqlClient getClient() {
    return client;
  }
}
