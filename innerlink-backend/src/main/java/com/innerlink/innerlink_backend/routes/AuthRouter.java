package com.innerlink.innerlink_backend.routes;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import com.innerlink.innerlink_backend.controllers.AuthController;

public class AuthRouter {
  private final AuthController authController;

  public AuthRouter(Vertx vertx) {
    this.authController = new AuthController(vertx);
  }

  public void setupRoutes(Router router) {
    router.post("/api/auth/login").handler(authController::login);
    router.post("/api/auth/register").handler(authController::register);
    router.post("/api/auth/logout").handler(authController::logout);
    router.get("/api/auth/me").handler(authController::getCurrentUser);
    System.out.println("Auth routes setup");
  }
}
