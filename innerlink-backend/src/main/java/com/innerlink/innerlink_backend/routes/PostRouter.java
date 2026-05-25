package com.innerlink.innerlink_backend.routes;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import com.innerlink.innerlink_backend.controllers.PostController;

public class PostRouter {
  private final PostController postController;

  public PostRouter(Vertx vertx) {
    this.postController = new PostController(vertx);
  }

  public void setupRoutes(Router router) {
    router.get("/api/reflections").handler(postController::getAllReflections);
    router.post("/api/reflections").handler(postController::createReflection);
  }
}
