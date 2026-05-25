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
    String email = body.getString("email");
    String passkey = body.getString("passkey");

    authService.login(email, passkey)
      .onSuccess(session -> {
        ctx.json(session);
      })
      .onFailure(err -> {
        ctx.response().setStatusCode(401).end("Invalid credentials");
      });
  }

  public void register(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    authService.register(body)
      .onSuccess(user -> {
        ctx.json(user);
      })
      .onFailure(err -> {
        ctx.response().setStatusCode(400).end(err.getMessage());
      });
  }

  public void logout(RoutingContext ctx) {
    String token = ctx.request().getHeader("Authorization");
    authService.logout(token);
    ctx.response().setStatusCode(200).end();
  }

  public void getCurrentUser(RoutingContext ctx) {
    String token = ctx.request().getHeader("Authorization");
    authService.getCurrentUser(token)
      .onSuccess(user -> ctx.json(user))
      .onFailure(err -> ctx.response().setStatusCode(401).end());
  }
}
