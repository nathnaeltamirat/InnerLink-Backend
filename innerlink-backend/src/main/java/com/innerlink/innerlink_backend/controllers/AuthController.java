package com.innerlink.innerlink_backend.controllers;

import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import com.innerlink.innerlink_backend.services.AuthService;

public class AuthController {
  private final AuthService authService;

  public AuthController(Vertx vertx) {
    this.authService = new AuthService(vertx);
  }

  public void login(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    authService.login(body.getString("email"), body.getString("passkey"))
      .onSuccess(ctx::json)
      .onFailure(err -> ctx.response().setStatusCode(401).end(err.getMessage()));
  }

  public void register(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    authService.register(body)
      .onSuccess(ctx::json)
      .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
  }

  public void getCurrentUser(RoutingContext ctx) {
    String token = ctx.request().getHeader("Authorization");
    authService.getCurrentUser(token)
      .onSuccess(ctx::json)
     .onFailure(err -> ctx.response().setStatusCode(401).end(err.getMessage()));
  }
}
