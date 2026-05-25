package com.innerlink.innerlink_backend.routes;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import com.innerlink.innerlink_backend.controllers.UserController;

public class UserRouter {
  private final UserController userController;

  public UserRouter(Vertx vertx) {
    this.userController = new UserController(vertx);
  }

  public void setupRoutes(Router router) {
    router.get("/api/users/:id").handler(userController::getUser);
    router.put("/api/users/:id").handler(userController::updateUser);
  }
}
