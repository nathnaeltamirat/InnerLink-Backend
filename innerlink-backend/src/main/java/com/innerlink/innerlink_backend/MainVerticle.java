package com.innerlink.innerlink_backend;

import com.innerlink.innerlink_backend.config.DatabaseConfig;
import com.innerlink.innerlink_backend.routes.AuthRouter;
import com.innerlink.innerlink_backend.routes.UserRouter;
import com.innerlink.innerlink_backend.routes.PostRouter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.Set;

public class MainVerticle extends AbstractVerticle {
  @Override
  public void start(Promise<Void> startPromise) {
    Router router = Router.router(vertx);

    router.route().handler(CorsHandler.create()
      .addOrigin("*")
      .allowedMethods(Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE))
      .allowedHeaders(Set.of("*")));

    router.route().handler(BodyHandler.create());

    DatabaseConfig.init(vertx);

    // Routes
    new AuthRouter(vertx).setupRoutes(router);
    new UserRouter(vertx).setupRoutes(router);
    new PostRouter(vertx).setupRoutes(router);

    // Static files
    router.route("/*").handler(StaticHandler.create("assets")
      .setCachingEnabled(false)
      .setIndexPage("index.html"));

    //  server
    vertx.createHttpServer()
      .requestHandler(router)
      .listen(8888)
      .onSuccess(server -> {
        System.out.println("Server on port 8888");
        startPromise.complete();
      })
      .onFailure(startPromise::fail);
  }
}
