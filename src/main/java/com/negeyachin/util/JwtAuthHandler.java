package com.negeyachin.util;

import com.negeyachin.common.ApiResponse;
import io.jsonwebtoken.Claims;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Vert.x route handler that validates the Bearer JWT and (optionally) checks a required role.
 *
 * Usage:
 *   router.get("/protected").handler(new JwtAuthHandler(jwtUtil));
 *   router.get("/admin-only").handler(new JwtAuthHandler(jwtUtil, "admin"));
 *
 * On success, places "userId", "email", "role" into RoutingContext data for downstream handlers.
 */
public class JwtAuthHandler implements Handler<RoutingContext> {

    private final JwtUtil jwtUtil;
    private final String  requiredRole; // null = any authenticated user

    public JwtAuthHandler(JwtUtil jwtUtil) {
        this(jwtUtil, null);
    }

    public JwtAuthHandler(JwtUtil jwtUtil, String requiredRole) {
        this.jwtUtil      = jwtUtil;
        this.requiredRole = requiredRole;
    }

    @Override
    public void handle(RoutingContext ctx) {
        String authHeader = ctx.request().getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            ctx.response().setStatusCode(401)
               .putHeader("Content-Type", "application/json")
               .end(ApiResponse.error(401, "Missing or malformed Authorization header.").encode());
            return;
        }

        String token  = authHeader.substring(7);
        Claims claims = jwtUtil.validate(token);

        if (claims == null) {
            ctx.response().setStatusCode(401)
               .putHeader("Content-Type", "application/json")
               .end(ApiResponse.error(401, "Invalid or expired token.").encode());
            return;
        }

        String role = (String) claims.get("role");

        if (requiredRole != null && !requiredRole.equalsIgnoreCase(role)) {
            ctx.response().setStatusCode(403)
               .putHeader("Content-Type", "application/json")
               .end(ApiResponse.error(403, "Access denied. Required role: " + requiredRole).encode());
            return;
        }

        // Inject principal data so downstream handlers can use them
        ctx.put("userId", claims.getSubject());
        ctx.put("email",  claims.get("email", String.class));
        ctx.put("role",   role);

        ctx.next();
    }
}
