package com.negeyachin.admin;

import com.negeyachin.auth.AuthRouter;
import com.negeyachin.common.ApiResponse;
import com.negeyachin.util.JwtAuthHandler;
import com.negeyachin.util.JwtUtil;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

/**
 * AdminRouter
 * ─────────────────────────────────────────────────────────────────────────
 * All routes require role = admin (enforced by JwtAuthHandler).
 * Mounted at: /api/admin
 *
 * ── Requirement 2 — Analytics ──────────────────────────────────────────
 *   GET  /api/admin/dashboard/analytics
 *        Returns: active souls, volunteers online, active conversations
 *                 + top-3 emergency flags (Emergency Monitoring panel)
 *
 * ── Requirement 3 — Community Members ──────────────────────────────────
 *   GET  /api/admin/community/members
 *        Query params: search, role, active, page, size, sortBy, sortDir
 *
 *   GET  /api/admin/community/members/:userId
 *        Single member detail
 *
 * ── Requirement 4 — User Actions ───────────────────────────────────────
 *   POST   /api/admin/community/members/:userId/make-volunteer
 *   POST   /api/admin/community/members/:userId/remove-volunteer
 *   DELETE /api/admin/community/members/:userId
 *   PATCH  /api/admin/community/members/:userId/role      body: {"role":"..."}
 *   GET    /api/admin/community/members/:userId/activity
 */
public class AdminRouter {

    public static Router create(Vertx vertx, JsonObject jwtConfig) {
        JwtUtil           jwtUtil           = new JwtUtil(jwtConfig);
        JwtAuthHandler    adminAuth         = new JwtAuthHandler(jwtUtil, "admin");
        AnalyticsService  analyticsService  = new AnalyticsService();
        CommunityService  communityService  = new CommunityService();
        Router            router            = Router.router(vertx);

        // ══════════════════════════════════════════════════════════════════
        //  REQUIREMENT 2 — Dashboard Analytics
        // ══════════════════════════════════════════════════════════════════

        /**
         * GET /api/admin/dashboard/analytics
         *
         * Response:
         * {
         *   "activeSoulsCount":          14208,
         *   "volunteersOnlineCount":     421,
         *   "activeConversationsCount":  892,
         *   "volunteersStatus":          "stable",
         *   "conversationsStatus":       "active",
         *   "emergencyMonitoring": [
         *     {
         *       "flagId":           "uuid",
         *       "userId":           "uuid",
         *       "alias":            "Anonymous#8842",
         *       "riskLevel":        "high",
         *       "flaggedContent":   "I just don't feel like the morning...",
         *       "status":           "open",
         *       "conversationType": "ai",
         *       "flaggedAt":        "2025-05-22T03:15:00"
         *     }, ...
         *   ],
         *   "snapshotTime": "2025-05-22T12:44:00"
         * }
         */
        router.get("/dashboard/analytics")
              .handler(adminAuth)
              .handler(ctx ->
                  analyticsService.getDashboardAnalytics()
                      .onSuccess(data ->
                          ctx.response().setStatusCode(200)
                             .putHeader("Content-Type", "application/json")
                             .end(ApiResponse.ok("Dashboard analytics retrieved.", data).encode())
                      )
                      .onFailure(err -> AuthRouter.handleError(ctx, err))
              );

        // ══════════════════════════════════════════════════════════════════
        //  REQUIREMENT 3 — Community Members (paginated + filtered)
        // ══════════════════════════════════════════════════════════════════

        /**
         * GET /api/admin/community/members
         *
         * Query Parameters:
         *   search  — partial match on alias or email (optional)
         *   role    — user | volunteer | admin (optional)
         *   active  — true | false (optional, omit for all)
         *   page    — 0-based page index (default: 0)
         *   size    — items per page (default: 10)
         *   sortBy  — created_at | email | role | last_login_at (default: created_at)
         *   sortDir — asc | desc (default: desc)
         *
         * Response:
         * {
         *   "content":       [ { userId, email, alias, avatarUrl, role, status, joinedAt } ],
         *   "totalElements": 14208,
         *   "totalPages":    1421,
         *   "currentPage":   0,
         *   "pageSize":      10
         * }
         */
        router.get("/community/members")
              .handler(adminAuth)
              .handler(ctx -> {
                  String  search  = ctx.queryParam("search").stream().findFirst().orElse(null);
                  String  role    = ctx.queryParam("role").stream().findFirst().orElse(null);
                  String  activeS = ctx.queryParam("active").stream().findFirst().orElse(null);
                  int     page    = intParam(ctx, "page",    0);
                  int     size    = intParam(ctx, "size",   10);
                  String  sortBy  = ctx.queryParam("sortBy").stream().findFirst().orElse("created_at");
                  String  sortDir = ctx.queryParam("sortDir").stream().findFirst().orElse("desc");
                  Boolean active  = activeS != null ? Boolean.parseBoolean(activeS) : null;

                  communityService.getMembers(search, role, active, page, size, sortBy, sortDir)
                      .onSuccess(data ->
                          ctx.response().setStatusCode(200)
                             .putHeader("Content-Type", "application/json")
                             .end(ApiResponse.ok("Community members retrieved.", data).encode())
                      )
                      .onFailure(err -> AuthRouter.handleError(ctx, err));
              });

        /**
         * GET /api/admin/community/members/:userId
         * Single member detail.
         */
        router.get("/community/members/:userId")
              .handler(adminAuth)
              .handler(ctx -> {
                  String userId = ctx.pathParam("userId");
                  communityService.getMemberById(userId)
                      .onSuccess(data ->
                          ctx.response().setStatusCode(200)
                             .putHeader("Content-Type", "application/json")
                             .end(ApiResponse.ok(data).encode())
                      )
                      .onFailure(err -> AuthRouter.handleError(ctx, err));
              });

        // ══════════════════════════════════════════════════════════════════
        //  REQUIREMENT 4 — User Actions
        // ══════════════════════════════════════════════════════════════════

        /**
         * POST /api/admin/community/members/:userId/make-volunteer
         * Promotes the user to volunteer role and creates a volunteer_profiles row.
         */
        router.post("/community/members/:userId/make-volunteer")
              .handler(adminAuth)
              .handler(ctx -> {
                  String userId = ctx.pathParam("userId");
                  communityService.makeVolunteer(userId)
                      .onSuccess(data ->
                          ctx.response().setStatusCode(200)
                             .putHeader("Content-Type", "application/json")
                             .end(ApiResponse.ok("User promoted to volunteer.", data).encode())
                      )
                      .onFailure(err -> AuthRouter.handleError(ctx, err));
              });

        /**
         * POST /api/admin/community/members/:userId/remove-volunteer
         * Demotes volunteer back to user and deactivates their volunteer_profiles row.
         */
        router.post("/community/members/:userId/remove-volunteer")
              .handler(adminAuth)
              .handler(ctx -> {
                  String userId = ctx.pathParam("userId");
                  communityService.removeVolunteer(userId)
                      .onSuccess(data ->
                          ctx.response().setStatusCode(200)
                             .putHeader("Content-Type", "application/json")
                             .end(ApiResponse.ok("Volunteer role removed.", data).encode())
                      )
                      .onFailure(err -> AuthRouter.handleError(ctx, err));
              });

        /**
         * DELETE /api/admin/community/members/:userId
         * Hard-deletes the user. Schema FK cascades handle all child rows.
         * Admin accounts are blocked from deletion via this endpoint.
         */
        router.delete("/community/members/:userId")
              .handler(adminAuth)
              .handler(ctx -> {
                  String userId = ctx.pathParam("userId");
                  communityService.deleteUser(userId)
                      .onSuccess(__ ->
                          ctx.response().setStatusCode(200)
                             .putHeader("Content-Type", "application/json")
                             .end(ApiResponse.ok("User removed from the sanctuary.", null).encode())
                      )
                      .onFailure(err -> AuthRouter.handleError(ctx, err));
              });

        /**
         * PATCH /api/admin/community/members/:userId/role
         * Adjusts user role to any valid value.
         *
         * Request body: { "role": "volunteer" }
         * Valid values: user | volunteer | admin
         */
        router.patch("/community/members/:userId/role")
              .handler(adminAuth)
              .handler(ctx -> {
                  String userId = ctx.pathParam("userId");
                  JsonObject body = ctx.body().asJsonObject();
                  if (body == null || !body.containsKey("role")) {
                      ctx.response().setStatusCode(400)
                         .putHeader("Content-Type", "application/json")
                         .end(ApiResponse.error(400, "Request body must contain 'role'.").encode());
                      return;
                  }
                  String newRole = body.getString("role", "").trim().toLowerCase();
                  communityService.adjustRole(userId, newRole)
                      .onSuccess(data ->
                          ctx.response().setStatusCode(200)
                             .putHeader("Content-Type", "application/json")
                             .end(ApiResponse.ok("Role updated to '" + newRole + "'.", data).encode())
                      )
                      .onFailure(err -> AuthRouter.handleError(ctx, err));
              });

        /**
         * GET /api/admin/community/members/:userId/activity
         * Returns enriched member data for the review activity panel.
         * Includes conversation counts, reflection counts, breathe session counts.
         */
        router.get("/community/members/:userId/activity")
              .handler(adminAuth)
              .handler(ctx -> {
                  String userId = ctx.pathParam("userId");
                  communityService.reviewActivity(userId)
                      .onSuccess(data ->
                          ctx.response().setStatusCode(200)
                             .putHeader("Content-Type", "application/json")
                             .end(ApiResponse.ok("Activity data retrieved.", data).encode())
                      )
                      .onFailure(err -> AuthRouter.handleError(ctx, err));
              });

        return router;
    }

    /** Safely parses an integer query param with a default fallback. */
    private static int intParam(io.vertx.ext.web.RoutingContext ctx, String name, int defaultVal) {
        try {
            return Integer.parseInt(
                ctx.queryParam(name).stream().findFirst().orElse(String.valueOf(defaultVal))
            );
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
