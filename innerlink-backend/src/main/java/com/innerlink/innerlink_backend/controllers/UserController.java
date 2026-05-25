package com.innerlink.innerlink_backend.controllers;

import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import com.innerlink.innerlink_backend.services.UserService;

public class UserController {
  private final UserService userService;

  public UserController(Vertx vertx) {
    this.userService = new UserService(vertx);
  }

  public void getUser(RoutingContext ctx) {
    String userId = ctx.request().getParam("id");
    userService.getUserById(userId)
      .onSuccess(ctx::json)
      .onFailure(err -> ctx.response().setStatusCode(404).end("User not found"));
  }

  public void updateUser(RoutingContext ctx) {
    String userId = ctx.request().getParam("id");
    JsonObject data = ctx.body().asJsonObject();
    userService.updateUser(userId, data)
      .onSuccess(ctx::json)
      .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
  }
}
