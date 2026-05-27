package com.innerlink.innerlink_backend.routes;

import com.innerlink.innerlink_backend.controllers.AdminController;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class AdminRouter {

    private final AdminController adminController;

    public AdminRouter(Vertx vertx) {
        this.adminController = new AdminController(vertx);
    }

    public void setupRoutes(Router router) {
        // Enforce parsing bodies on admin endpoints to prevent payload dropouts
        router.route("/api/admin/*").handler(BodyHandler.create());

        // Dashboard Metrics
        router.get("/api/admin/dashboard/analytics").handler(adminController::getDashboardAnalytics);

        // Community Membership Controls
        router.get("/api/admin/community/members").handler(adminController::getCommunityMembers);
        router.get("/api/admin/community/members/:userId/activity").handler(adminController::getMemberActivity);
        router.patch("/api/admin/community/members/:userId/role").handler(adminController::updateMemberRole);
        router.delete("/api/admin/community/members/:userId").handler(adminController::deleteUser);

        // Explicit Volunteer Toggles (Path-param driven)
        router.post("/api/admin/community/members/:userId/make-volunteer").handler(adminController::promoteToVolunteer);
        router.post("/api/admin/community/members/:userId/remove-volunteer").handler(adminController::demoteToUser);

        // Live Emergency Flags Pipeline Feed
        router.get("/api/admin/emergency/flags").handler(adminController::getEmergencyFlagsFeed);
        router.patch("/api/admin/emergency/flags/:flagId/status").handler(adminController::updateEmergencyFlagStatus);
    }
}