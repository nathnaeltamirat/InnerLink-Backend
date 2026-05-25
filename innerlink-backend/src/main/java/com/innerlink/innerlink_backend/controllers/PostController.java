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
      .onSuccess(reflections -> ctx.json(reflections))
      .onFailure(err -> ctx.response().setStatusCode(500).end("Server error"));
  }

  public void createReflection(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    String userId = ctx.request().getHeader("X-User-Id");
    if (userId == null) userId = body.getString("userId");
    body.put("userId", userId);
    postService.createReflection(body)
      .onSuccess(reflection -> ctx.json(reflection))
      .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
  }

  public void getReflection(RoutingContext ctx) {
    String id = ctx.request().getParam("id");
    postService.getReflectionById(id)
      .onSuccess(reflection -> ctx.json(reflection))
      .onFailure(err -> ctx.response().setStatusCode(404).end("Reflection not found"));
  }

  public void deleteReflection(RoutingContext ctx) {
    String id = ctx.request().getParam("id");
    postService.deleteReflection(id)
      .onSuccess(v -> ctx.response().setStatusCode(200).end())
      .onFailure(err -> ctx.response().setStatusCode(404).end("Reflection not found"));
  }
}
