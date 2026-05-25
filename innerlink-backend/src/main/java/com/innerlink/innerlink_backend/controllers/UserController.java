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
      .onSuccess(user -> ctx.json(user))
      .onFailure(err -> ctx.response().setStatusCode(404).end("User not found"));
  }

  public void updateUser(RoutingContext ctx) {
    String userId = ctx.request().getParam("id");
    JsonObject data = ctx.body().asJsonObject();
    userService.updateUser(userId, data)
      .onSuccess(user -> ctx.json(user))
      .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
  }

  public void getProfile(RoutingContext ctx) {
    String userId = ctx.request().getParam("id");
    userService.getProfile(userId)
      .onSuccess(profile -> ctx.json(profile))
      .onFailure(err -> ctx.response().setStatusCode(404).end("Profile not found"));
  }

  public void toggleAvailability(RoutingContext ctx) {
    String userId = ctx.request().getParam("id");
    JsonObject data = ctx.body().asJsonObject();
    boolean isAvailable = data.getBoolean("isAvailable");
    userService.toggleAvailability(userId, isAvailable)
      .onSuccess(result -> ctx.json(new JsonObject().put("success", true)))
      .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
  }

  public void getVolunteers(RoutingContext ctx) {
    userService.getAllVolunteers()
      .onSuccess(volunteers -> ctx.json(volunteers))
      .onFailure(err -> ctx.response().setStatusCode(500).end("Server error"));
  }

  public void getAvailableVolunteers(RoutingContext ctx) {
    userService.getAvailableVolunteers()
      .onSuccess(volunteers -> ctx.json(volunteers))
      .onFailure(err -> ctx.response().setStatusCode(500).end("Server error"));
  }
}
