package com.innerlink.innerlink_backend.controllers;

import com.innerlink.innerlink_backend.services.AdminService;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class AdminController {

    private final AdminService adminService;

    public AdminController(Vertx vertx) {
        this.adminService = new AdminService();
    }

    private void sendSuccess(RoutingContext ctx, Object data) {
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(200)
            .end(new JsonObject().put("success", true).put("data", data).encode());
    }

    private void sendFailure(RoutingContext ctx, int statusCode, String message) {
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(statusCode)
            .end(new JsonObject().put("success", false).put("message", message).encode());
    }

    public void getDashboardAnalytics(RoutingContext ctx) {
        adminService.fetchAnalytics()
            .onSuccess(data -> sendSuccess(ctx, data))
            .onFailure(err -> sendFailure(ctx, 500, err.getMessage()));
    }

    public void getCommunityMembers(RoutingContext ctx) {
        try {
            int page = Integer.parseInt(ctx.request().getParam("page") != null ? ctx.request().getParam("page") : "0");
            int size = Integer.parseInt(ctx.request().getParam("size") != null ? ctx.request().getParam("size") : "5");
            String search = ctx.request().getParam("search");
            String role = ctx.request().getParam("role");
            String activeParam = ctx.request().getParam("active");
            String sortBy = ctx.request().getParam("sortBy") != null ? ctx.request().getParam("sortBy") : "created_at";

            adminService.fetchMembers(page, size, search, role, activeParam, sortBy)
                .onSuccess(data -> sendSuccess(ctx, data))
                .onFailure(err -> sendFailure(ctx, 500, err.getMessage()));
        } catch (Exception e) {
            sendFailure(ctx, 400, "Malformed structural request criteria: " + e.getMessage());
        }
    }

    public void getMemberActivity(RoutingContext ctx) {
        String userId = ctx.pathParam("userId");
        adminService.fetchMemberActivity(userId)
            .onSuccess(data -> sendSuccess(ctx, data))
            .onFailure(err -> sendFailure(ctx, 500, err.getMessage()));
    }

    public void updateMemberRole(RoutingContext ctx) {
        String userId = ctx.pathParam("userId");
        JsonObject body = ctx.body().asJsonObject();

        if (body == null || !body.containsKey("role")) {
            sendFailure(ctx, 400, "Missing structural data modification parameter: 'role'");
            return;
        }

        String targetRole = body.getString("role");
        adminService.modifyMemberRole(userId, targetRole)
            .onSuccess(v -> sendSuccess(ctx, new JsonObject().put("message", "Account role re-allocated structural status successfully.")))
            .onFailure(err -> sendFailure(ctx, 500, err.getMessage()));
    }

    public void promoteToVolunteer(RoutingContext ctx) {
        String userId = ctx.pathParam("userId");
        adminService.modifyMemberRole(userId, "volunteer")
            .onSuccess(v -> sendSuccess(ctx, new JsonObject().put("message", "User promoted to volunteer profile.")))
            .onFailure(err -> sendFailure(ctx, 500, err.getMessage()));
    }

    public void demoteToUser(RoutingContext ctx) {
        String userId = ctx.pathParam("userId");
        adminService.modifyMemberRole(userId, "user")
            .onSuccess(v -> sendSuccess(ctx, new JsonObject().put("message", "Volunteer demoted back to user profile.")))
            .onFailure(err -> sendFailure(ctx, 500, err.getMessage()));
    }

    public void deleteUser(RoutingContext ctx) {
        String userId = ctx.pathParam("userId");
        adminService.removeUserAccount(userId)
            .onSuccess(v -> sendSuccess(ctx, new JsonObject().put("message", "User account wiped from dataset records successfully.")))
            .onFailure(err -> sendFailure(ctx, 500, err.getMessage()));
    }

    public void getEmergencyFlagsFeed(RoutingContext ctx) {
        adminService.fetchEmergencyFlagsFeed()
            .onSuccess(data -> sendSuccess(ctx, data))
            .onFailure(err -> sendFailure(ctx, 500, err.getMessage()));
    }

    public void updateEmergencyFlagStatus(RoutingContext ctx) {
        String flagId = ctx.pathParam("flagId");
        JsonObject body = ctx.body().asJsonObject();

        if (body == null || !body.containsKey("status")) {
            sendFailure(ctx, 400, "Missing contextual update tracking parameter: 'status'");
            return;
        }

        String targetStatus = body.getString("status");
        adminService.changeFlagTriageStatus(flagId, targetStatus)
            .onSuccess(v -> sendSuccess(ctx, new JsonObject().put("message", "Flag structural tracking state escalated successfully.")))
            .onFailure(err -> sendFailure(ctx, 500, err.getMessage()));
    }
}