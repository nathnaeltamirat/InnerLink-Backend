package com.innerlink.innerlink_backend;

import com.innerlink.innerlink_backend.chat.service.ChatSessionService;
import com.innerlink.innerlink_backend.chat.verticle.ChatRouter;
import com.innerlink.innerlink_backend.chat.verticle.ChatVerticle;
import com.innerlink.innerlink_backend.config.DatabaseConfig;
import com.innerlink.innerlink_backend.routes.AuthRouter;
import com.innerlink.innerlink_backend.routes.NotificationRouter;
import com.innerlink.innerlink_backend.routes.UserRouter;
import com.innerlink.innerlink_backend.routes.PostRouter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.Set;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) {
    Router mainRouter = Router.router(vertx);

    // CORS
    mainRouter.route().handler(CorsHandler.create()
        .addOrigin("*")
        .allowedMethods(Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE))
        .allowedHeaders(Set.of("*")));
    ChatVerticle chatVerticle = new ChatVerticle();
    ChatSessionService chatSessionService = new ChatSessionService(chatVerticle);

    ChatRouter chatRouter = new ChatRouter(chatSessionService);
    chatRouter.setupRoutes(mainRouter);

    vertx.deployVerticle(chatVerticle);
    mainRouter.route().handler(BodyHandler.create());
    DatabaseConfig.init(vertx);
    setupRoutes(mainRouter);

    // Static Assets
    mainRouter.route("/*").handler(StaticHandler.create("assets")
        .setCachingEnabled(false).setIndexPage("index.html"));

    vertx.createHttpServer()
        .requestHandler(mainRouter)
        .listen(8888)
        .onSuccess(server -> {
          System.out.println("HTTP server started on port " + server.actualPort());
          startPromise.complete();
        })
        .onFailure(err -> {
          System.out.println("Failed to start HTTP server: " + err.getMessage());
          startPromise.fail("Failed to start server");
        });
  }

  private void setupRoutes(Router router) {
    // Auth Routes
    AuthRouter authRouter = new AuthRouter(vertx);
    authRouter.setupRoutes(router);

    UserRouter userRouter = new UserRouter(vertx);
    userRouter.setupRoutes(router);

    PostRouter postRouter = new PostRouter(vertx);
    postRouter.setupRoutes(router);

    NotificationRouter notificationRouter = new NotificationRouter(vertx);
    notificationRouter.setupRoutes(router);

    System.out.println("Routes setup complete");
  }

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();

    vertx.deployVerticle(new MainVerticle())
        .onSuccess(id -> {
          System.out.println(" MainVerticle deployed successfully with ID: " + id);
        })
        .onFailure(err -> {
          System.err.println(" Failed to deploy MainVerticle: " + err.getMessage());
          vertx.close();
        });
  }
}
