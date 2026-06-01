package com.innerlink.innerlink_backend;

import com.innerlink.innerlink_backend.chat.router.ChatRouter;
import com.innerlink.innerlink_backend.chat.verticle.ChatVerticle;
import com.innerlink.innerlink_backend.config.DatabaseConfig;
import com.innerlink.innerlink_backend.routes.AdminRouter;
import com.innerlink.innerlink_backend.routes.AuthRouter;
import com.innerlink.innerlink_backend.routes.UserRouter;
import com.innerlink.innerlink_backend.routes.PostRouter;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;

import java.util.Set;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) {

    DatabaseConfig.init(vertx)
        .compose(v -> {
          System.out.println("DB verified. Setting up routing framework...");

          Router router = Router.router(vertx);

          // Configure CORS
          router.route().handler(CorsHandler.create()
              .addOrigin("*")
              .allowedMethods(Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PATCH,HttpMethod.PUT, HttpMethod.DELETE))
              .allowedHeaders(Set.of("*")));

          router.route().handler(BodyHandler.create());

          ChatRouter chatRouter = new ChatRouter(vertx);
          chatRouter.setupRoutes(router);

          new AdminRouter(vertx).setupRoutes(router);
          new AuthRouter(vertx).setupRoutes(router);
          new UserRouter(vertx).setupRoutes(router);
          new PostRouter(vertx).setupRoutes(router);

          ChatVerticle chatVerticle = new ChatVerticle();
          return vertx.deployVerticle(chatVerticle)
              .map(deploymentId -> router);
        })
        .compose(router -> {

          return vertx.createHttpServer()
              .requestHandler(router)
              .listen(8888);
        })
        .onSuccess(server -> {
          System.out.println("listening on port 8888");
          startPromise.complete();
        })
        .onFailure(err -> {
          System.err.println("Application startup aborted: " + err.getMessage());
          startPromise.fail(err);
        });
  }
}
