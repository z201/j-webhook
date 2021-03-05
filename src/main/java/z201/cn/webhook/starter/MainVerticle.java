package z201.cn.webhook.starter;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.json.JSON;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

/**
 * @author z201.coding@gmail.com
 */
public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    HttpServer server = vertx.createHttpServer();
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router
      .get("/")
      .respond(ctx -> ctx.response()
        .putHeader("Content-Type", "text/plain")
        .end("Succeeded"));
    router
      .post("/info")
      .handler(ctx -> {
        if (!config().containsKey(ConfKey.GITHUB_SECRET_KEY)) {
          System.out.println("The configuration file github.webhook.secret is not set");
        }
        String secret = config().getString(ConfKey.GITHUB_SECRET_KEY);
        JsonArray repositoryArray = config().getJsonArray(ConfKey.GITHUB_REPOSITORY_KEY);
        Map<String, String> repositoryMap = new HashMap<>();
        if (null == repositoryArray) {
          System.out.println("The configuration file github.repository is not set");
        } else {
          if (repositoryArray.isEmpty()) {
            System.out.println("The configuration file github.repository is not set");
          }
          repositoryArray.stream().filter(Objects::nonNull).forEach(i -> {
            String json = i.toString();
            JsonObject jsonObject = new JsonObject(json);
            if (jsonObject.isEmpty()) {
              System.out.println("The configuration file is not set");
              return;
            }
            if (!jsonObject.containsKey(ConfKey.NAME_KEY)) {
              System.out.println("The configuration file github.repository.name is not set");
              return;
            }
            if (StrUtil.isEmptyIfStr(jsonObject.getString(ConfKey.SSH_URL_KEY))) {
              System.out.println("The configuration file github.repository.ssh.url is not set");
              return;
            }
            if (StrUtil.isEmptyIfStr(jsonObject.getString(ConfKey.SCRIPT_PATH_KEY))) {
              System.out.println("The configuration file github.repository.script.path is not set");
              return;
            }
            repositoryMap.put(jsonObject.getString(ConfKey.SSH_URL_KEY), jsonObject.getString(ConfKey.SCRIPT_PATH_KEY));
          });
        }
        MultiMap multiMap = ctx.request().headers();
        String signature = multiMap.get("X-Hub-Signature"); // 被触发的事件的名称
        JsonObject payloadJson = ctx.getBodyAsJson();
        Boolean state = false;
        if (StrUtil.isEmptyIfStr(signature) || payloadJson.isEmpty()) {
          ctx.response()
            .putHeader("Content-Type", "text/plain")
            .setStatusCode(400);
          ctx.end("Fail!");
        }else{
          if (payloadJson.containsKey(ConfKey.REPOSITORY_KEY)) {
            if (payloadJson.getJsonObject(ConfKey.REPOSITORY_KEY).containsKey(ConfKey.GITHUB_REPOSITORY_SHH_URL_KEY)) {
              String url = payloadJson.getJsonObject(ConfKey.REPOSITORY_KEY).getString(ConfKey.GITHUB_REPOSITORY_SHH_URL_KEY);
              if (repositoryMap.containsKey(url)) {
                String getSignature = getSignature(payloadJson.toString(), secret);
                if (signature.equals(getSignature)) {
                  String path = repositoryMap.get(url);
                  if (FileUtil.exist(path)) {
                    FileReader fileReader = new FileReader(path);
                    String content = fileReader.readString();
                    try {
                      process(content);
                      state = true;
                    } catch (IOException e) {
                      System.out.println(e.getMessage());
                    }
                  }
                }
              }
            }
          }
        }
        if (!state) {
          ctx.response()
            .putHeader("Content-Type", "text/plain")
            .setStatusCode(403);
          ctx.end("Fail!");
        } else {
          ctx.response()
            .putHeader("Content-Type", "text/plain")
            .end("Succeeded!");
        }
      });

    server.requestHandler(router).listen(config().getInteger(ConfKey.PORT_KEY, 8888), http -> {
      if (http.succeeded()) {
        startPromise.complete();
      } else {
        startPromise.fail(http.cause());
      }
    });
  }

  private void process(String content) throws IOException {
    List<String> shell = new ArrayList<>();
    shell.add("sh");
    shell.add("-c");
    shell.add(content);
    ProcessBuilder pb = new ProcessBuilder(shell);
    Process process = pb.start();
    String line;
    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
    while ((line = reader.readLine()) != null) {
      System.out.println(line);
    }
    reader.close();
  }

  /**
   * @param payload
   * @return
   */
  private String getSignature(String payload, String secret) {
    HMac hMac = SecureUtil.hmacSha1(secret);
    final byte[] signature = hMac.digest(payload);
    return "sha1=" + HexUtil.encodeHexStr(signature);
  }
}
