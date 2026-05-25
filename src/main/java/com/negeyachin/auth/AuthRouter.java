package com.negeyachin.auth;

import com.negeyachin.common.ApiResponse;
import com.negeyachin.util.JwtAuthHandler;
import com.negeyachin.util.JwtUtil;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AuthRouter
 * Mounted at: /api/auth
 *
 *  POST /api/auth/signup   — register
 *  POST /api/auth/signin   — login
 *  POST /api/auth/signout  — logout (requires JWT)
 */
public class AuthRouter {

    private static final Logger log = LoggerFactory.getLogger(AuthRouter.class);

    public static Router create(Vertx vertx, JsonObject jwtConfig) {
        JwtUtil     jwtUtil     = new JwtUtil(jwtConfig);
        AuthService authService = new AuthService(jwtUtil);
        Router      router      = Router.router(vertx);

        // ── POST /signup ──────────────────────────────────────────────────
        router.post("/signup").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) {
                ctx.response().setStatusCode(400)
                   .putHeader("Content-Type", "application/json")
                   .end(ApiResponse.error(400, "Request body must be JSON.").encode());
                return;
            }
            authService.signUp(body)
                .onSuccess(data ->
                    ctx.response().setStatusCode(201)
                       .putHeader("Content-Type", "application/json")
                       .end(ApiResponse.ok("Account created. Welcome quietly.", data).encode())
                )
                .onFailure(err -> handleError(ctx, err));
        });

        // ── POST /signin ──────────────────────────────────────────────────
        router.post("/signin").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) {
                ctx.response().setStatusCode(400)
                   .putHeader("Content-Type", "application/json")
                   .end(ApiResponse.error(400, "Request body must be JSON.").encode());
                return;
            }
            String ip        = ctx.request().remoteAddress().host();
            String userAgent = ctx.request().getHeader("User-Agent");

            authService.signIn(body, ip, userAgent)
                .onSuccess(data ->
                    ctx.response().setStatusCode(200)
                       .putHeader("Content-Type", "application/json")
                       .end(ApiResponse.ok("Enter quietly.", data).encode())
                )
                .onFailure(err -> handleError(ctx, err));
        });

        // ── POST /signout  (requires valid JWT) ───────────────────────────
        router.post("/signout")
              .handler(new JwtAuthHandler(jwtUtil))
              .handler(ctx -> {
                  String userId = ctx.get("userId");
                  authService.signOut(userId)
                      .onSuccess(__ ->
                          ctx.response().setStatusCode(200)
                             .putHeader("Content-Type", "application/json")
                             .end(ApiResponse.ok("You have left quietly.", null).encode())
                      )
                      .onFailure(err -> handleError(ctx, err));
              });

        return router;
    }

    /** Maps error prefix codes to HTTP status codes. */
    static void handleError(io.vertx.ext.web.RoutingContext ctx, Throwable err) {
        String msg = err.getMessage() != null ? err.getMessage() : "Unexpected error.";
        int status;
        String body;

        if (msg.startsWith("VALIDATION:")) {
            status = 400; body = msg.substring("VALIDATION:".length());
        } else if (msg.startsWith("CONFLICT:")) {
            status = 409; body = msg.substring("CONFLICT:".length());
        } else if (msg.startsWith("UNAUTHORIZED:")) {
            status = 401; body = msg.substring("UNAUTHORIZED:".length());
        } else if (msg.startsWith("NOT_FOUND:")) {
            status = 404; body = msg.substring("NOT_FOUND:".length());
        } else if (msg.startsWith("BAD_REQUEST:")) {
            status = 400; body = msg.substring("BAD_REQUEST:".length());
        } else {
            status = 500;
            body   = "An unexpected error occurred.";
            log.error("Unhandled error", err);
        }

        ctx.response().setStatusCode(status)
           .putHeader("Content-Type", "application/json")
           .end(ApiResponse.error(status, body).encode());
    }
}
