package com.negeyachin.common;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;

/**
 * Singleton wrapper for the Vert.x async MySQL connection pool.
 * Call DbPool.init() once at startup, then DbPool.pool() everywhere else.
 */
public final class DbPool {

    private static MySQLPool pool;

    private DbPool() {}

    public static void init(Vertx vertx, JsonObject dbConfig) {
        MySQLConnectOptions connectOptions = new MySQLConnectOptions()
            .setHost(dbConfig.getString("host", "localhost"))
            .setPort(dbConfig.getInteger("port", 3306))
            .setDatabase(dbConfig.getString("database", "negeyachin"))
            .setUser(dbConfig.getString("user", "root"))
            .setPassword(dbConfig.getString("password", ""))
            .setCharset("utf8mb4");

        PoolOptions poolOptions = new PoolOptions()
            .setMaxSize(dbConfig.getInteger("maxPoolSize", 10));

        pool = MySQLPool.pool(vertx, connectOptions, poolOptions);
    }

    public static MySQLPool pool() {
        if (pool == null) throw new IllegalStateException("DbPool not initialised. Call DbPool.init() first.");
        return pool;
    }
}
