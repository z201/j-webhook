package z201.cn.webhook.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.Map;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    HttpServer server = vertx.createHttpServer();
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router
      .post("/info")
      .handler(ctx -> {
        MultiMap multiMap = ctx.request().headers();
        for (Map.Entry<String, String> stringStringEntry : multiMap) {
          System.out.println(stringStringEntry.getKey() + " " + stringStringEntry.getValue());
        }
        System.out.println(ctx.getBodyAsJson().toString());
        ctx.response()
          .putHeader("Content-Type", "text/plain")
          .end("hello world!");
      });
    server.requestHandler(router).listen(8888, http -> {
      if (http.succeeded()) {
        startPromise.complete();
        System.out.println("HTTP server started on port 8888");
      } else {
        startPromise.fail(http.cause());
      }
    });
  }


}
