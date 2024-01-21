package ir.farshad.qrcodescanner;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

public class MainVerticle extends AbstractVerticle {

  public static Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);
  private static String OUTPUT_FILE_PATH = "";

  private static List<String> tickets;
  private static List<String> inside;
  private final String[] arg;


  public static void main(String[] args) {
    LOGGER.info("------------------------------------------------------------------\n\n");
    LOGGER.info(Arrays.toString(args));
    LOGGER.info("------------------------------------------------------------------");

    final Vertx vertx = Vertx.vertx();

    // Deploy the verticle
    vertx.deployVerticle(new MainVerticle(args));
  }

  public MainVerticle(String[] arg) {
    this.arg = arg;
  }

  private static Options getOptions() {
    try {
      Options options = new Options();

      Option inputFileOption = new Option("i", "inputFile", true, "Input File");
      Option outputFileOption = new Option("o", "outputFile", true, "Output File");
      Option ksFileOption = new Option("k", "ksFile", true, "Keystore File");
      Option ksPassOption = new Option("p", "ksPass", true, "Keystore Password");

      options.addOption(inputFileOption);
      options.addOption(outputFileOption);
      options.addOption(ksFileOption);
      options.addOption(ksPassOption);
      return options;
    }catch (Exception e){
      throw new RuntimeException(e);
    }
  }

  @Override
  public void start(Promise<Void> startPromise) {
    String ksPass = "";
    String ksPath = "";
    try {
      LOGGER.info("Main Verticle Deployed");
      Options options = getOptions();
      CommandLineParser parser = new DefaultParser();
      CommandLine cmd = parser.parse(options, arg);

      LOGGER.info(Arrays.toString(arg));
      String inputFile = cmd.getOptionValue("inputFile");
      LOGGER.info(inputFile);
      OUTPUT_FILE_PATH = cmd.getOptionValue("outputFile");
      LOGGER.info(OUTPUT_FILE_PATH);
      ksPath = cmd.getOptionValue("ksFile");
      ksPass = cmd.getOptionValue("ksPass");
      if (!Files.exists(Path.of(OUTPUT_FILE_PATH))) {
        Files.createFile(Path.of(OUTPUT_FILE_PATH));
      }

      tickets = Files.readAllLines(Path.of(inputFile));
      inside = Files.readAllLines(Path.of(OUTPUT_FILE_PATH));
      LOGGER.info(tickets);

    } catch (IOException | ParseException e) {
      throw new RuntimeException(e);
    }

    Router router = Router.router(vertx);

    // Serve static resources (HTML, CSS, JS, etc.)
    router.route().handler(StaticHandler.create()
      .setCachingEnabled(false)
      .setDirectoryTemplate("templates")
      .setIndexPage("templates/index.html"));

    // Enable reading the request body
    router.route().handler(BodyHandler.create());

    // Route to handle the main page
    router.get("/").handler(this::handleMainPage);

    // Route to handle QR code validation
    router.get("/validate").handler(this::handleQRCodeValidation);

    try {

      JksOptions jksOptions = new JksOptions();
      jksOptions.setPassword(ksPass);
      jksOptions.setPath(ksPath);


      HttpServerOptions options = new HttpServerOptions()
        .setSsl(true)
        .setKeyStoreOptions(jksOptions)
        .setLogActivity(true);

      vertx.createHttpServer(options)
        .requestHandler(router)
        .listen(443, http -> {
          if (http.succeeded()) {
            startPromise.complete();
            LOGGER.info("HTTP server started on port 443");
          } else {
            startPromise.fail(http.cause());
          }
        });
    } catch (Exception e) {
      LOGGER.error(e.getMessage());
    }

  }

  private void handleMainPage(RoutingContext routingContext) {
    ThymeleafTemplateEngine engine = ThymeleafTemplateEngine.create(vertx);


    engine.render(routingContext.data(), "templates/index.html", res -> {
      if (res.succeeded()) {
        routingContext.response().putHeader("Content-Type", "text/html;charset=utf-8'");

        routingContext.response().end(res.result());
      } else {
        routingContext.fail(res.cause());
      }
    });
  }

  private void handleQRCodeValidation(RoutingContext routingContext) {
    String qrcode = routingContext.request().getParam("qrcode");


    // Validate the QR code by checking against the content of the input file
    boolean exists = validateQRCode(qrcode);
    boolean isInside = inside.contains(qrcode);
    if (!isInside) {
      // Write the result to the output file
      writeResultToFile(qrcode, exists);
    }


    // Respond with the validation result as JSON
    JsonObject response = new JsonObject().put("exists", exists).put("isInside", isInside);
    routingContext.response()
      .putHeader("Content-Type", "application/json")
      .end(response.encode());
  }

  private boolean validateQRCode(String qrcode) {
    // Read the content of the input file and check if the QR code exists
    return tickets.contains(qrcode);

  }

  private void writeResultToFile(String qrcode, boolean exists) {
    // Write the QR code and validation result to the output file
    if (exists) {
      try {
        inside.add(qrcode);
//        String result = qrcode + " " + (exists ? "exists\n" : "not found\n");
        String result = qrcode + "\n";


        Files.write(Path.of(OUTPUT_FILE_PATH), result.getBytes(), StandardOpenOption.APPEND);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}