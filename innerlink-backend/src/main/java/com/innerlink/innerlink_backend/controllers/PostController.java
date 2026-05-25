package com.innerlink.innerlink_backend.controllers;

import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import com.innerlink.innerlink_backend.services.PostService;

public class PostController {
  private final PostService postService;

  public PostController(Vertx vertx) {
    this.postService = new PostService(vertx);
  }

  public void getAllReflections(RoutingContext ctx) {
    postService.getAllReflections()
      .onSuccess(ctx::json)
      .onFailure(err -> ctx.response().setStatusCode(500).end("Server error"));
  }

  public void createReflection(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    String userId = ctx.request().getHeader("X-User-Id");
    if (userId == null) userId = body.getString("userId");
    body.put("userId", userId);
    postService.createReflection(body)
      .onSuccess(ctx::json)
      .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
  }
}
