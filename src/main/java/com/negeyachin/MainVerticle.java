package com.negeyachin;

import com.negeyachin.admin.AdminRouter;
import com.negeyachin.auth.AuthRouter;
import com.negeyachin.common.DbPool;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MainVerticle — bootstraps the Vert.x HTTP server, database pool,
 * and mounts all route groups.
 *
 * Run:  java -jar target/negeyachin-backend-1.0.0.jar
 */
public class MainVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new MainVerticle(), res -> {
            if (res.failed()) {
                log.error("Failed to deploy MainVerticle", res.cause());
                System.exit(1);
            }
        });
    }

    @Override
    public void start(Promise<Void> startPromise) {
        // ── 1. Load config ────────────────────────────────────────────────
        ConfigRetriever retriever = ConfigRetriever.create(vertx,
            new ConfigRetrieverOptions().addStore(
                new ConfigStoreOptions()
                    .setType("file")
                    .setFormat("json")
                    .setConfig(new JsonObject().put("path", "src/main/resources/config.json"))
            ));

        retriever.getConfig(cfgResult -> {
            if (cfgResult.failed()) {
                startPromise.fail(cfgResult.cause());
                return;
            }
            JsonObject config = cfgResult.result();

            // ── 2. Initialise DB pool ────────────────────────────────────
            DbPool.init(vertx, config.getJsonObject("db"));

            // ── 3. Build router ──────────────────────────────────────────
            Router router = Router.router(vertx);

            // CORS — allow all origins (tighten in production)
            router.route().handler(
                CorsHandler.create()
                    .addRelativeOrigin(".*")
                    .allowedMethod(io.vertx.core.http.HttpMethod.GET)
                    .allowedMethod(io.vertx.core.http.HttpMethod.POST)
                    .allowedMethod(io.vertx.core.http.HttpMethod.PUT)
                    .allowedMethod(io.vertx.core.http.HttpMethod.PATCH)
                    .allowedMethod(io.vertx.core.http.HttpMethod.DELETE)
                    .allowedMethod(io.vertx.core.http.HttpMethod.OPTIONS)
                    .allowedHeader("Content-Type")
                    .allowedHeader("Authorization")
            );

            // Body handler (max 2 MB)
            router.route().handler(BodyHandler.create().setBodyLimit(2 * 1024 * 1024));

            // ── 4. Mount route groups ────────────────────────────────────
            JsonObject jwtConfig = config.getJsonObject("jwt");

            router.mountSubRouter("/api/auth",  AuthRouter.create(vertx, jwtConfig));
            router.mountSubRouter("/api/admin", AdminRouter.create(vertx, jwtConfig));

            // Health check
            router.get("/api/health").handler(ctx ->
                ctx.response()
                   .putHeader("Content-Type", "application/json")
                   .end(new JsonObject().put("status", "UP").put("app", "Negeyachin").encode())
            );

            // Global 404
            router.route().handler(ctx ->
                ctx.response().setStatusCode(404)
                   .putHeader("Content-Type", "application/json")
                   .end(new JsonObject()
                       .put("success", false)
                       .put("message", "Route not found").encode())
            );

            // ── 5. Start HTTP server ─────────────────────────────────────
            int port = config.getJsonObject("http", new JsonObject()).getInteger("port", 8080);
            vertx.createHttpServer()
                 .requestHandler(router)
                 .listen(port, res -> {
                     if (res.succeeded()) {
                         log.info("Negeyachin server listening on port {}", port);
                         startPromise.complete();
                     } else {
                         startPromise.fail(res.cause());
                     }
                 });
        });
    }
}
