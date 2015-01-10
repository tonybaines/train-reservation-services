package trains.data;

import com.google.gson.*;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.platform.Verticle;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Iterator;
import java.util.Map;

public class TrainDataService extends Verticle {

  public static final int BAD_REQUEST = 400;
  public static final int NOT_FOUND = 404;
  public static final int CONFLICT = 409;

  @Override
  public void start() {
    RouteMatcher rm = new RouteMatcher();

    final JsonObject data = readTrainData();

    /*
    Get data for example about the train with id "express_2000" like this:
    http://localhost:8081/data_for_train/express_2000

    this will return a json document with information about the seats that this train
    has. The document you get back will look for example like this:
    {"seats": {
      "1A": {"booking_reference": "", "seat_number": "1", "coach": "A"},
      "2A": {"booking_reference": "", "seat_number": "2", "coach": "A"}
    }}

    Note I've left out all the extraneous details about where the train is going to and
    from, at what time, whether there's a buffet car etc. All that's there is which seats
    the train has, and if they are already booked.
    */
    rm.get("/data_for_train/:trainId", new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        String trainId = req.params().get("trainId");
        container.logger().warn("Handling a request for /data_for_train/" + trainId);
        req.response().putHeader("Content-Type", "application/json");
        if (data.has(trainId)) {
          req.response().end(prettyJsonFrom(data.get(trainId)));
        } else {
          error(req.response(), String.format("Train with ID %s was not found", trainId), NOT_FOUND);
        }
      }
    });

    /*
    To reserve seats on a train, you'll need to make a POST request to this url:
    http://localhost:8081/reserve
    and attach form data for which seats to reserve. There should be three fields:
    "train_id", "seats", "booking_reference"
    The "seats" field should be a json encoded list of seat ids, for example:
    '["1A", "2A"]'
    The other two fields are ordinary strings. Note the server will prevent you
    from booking a seat that is already reserved with another booking reference.
    */
    rm.post("/reserve", new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        container.logger().warn("Handling a request for /reserve");

        req.expectMultiPart(true).endHandler(new VoidHandler() {
          public void handle() {
            try {
              final MultiMap attrs = req.formAttributes();
              final String trainId = attrs.get("train_id");
              final String seatsJson = attrs.get("seats");
              final String bookingRef = attrs.get("booking_reference");

              req.response().putHeader("Content-Type", "application/json");

              // Validate the request
              if (emptyOrNull(trainId, seatsJson, bookingRef)) {
                error(req.response(), String.format("One or more request attributes missing: [train_id=%s, seats=%s, booking_reference=%s]", trainId, seatsJson, bookingRef), BAD_REQUEST);
                return;
              } else if (!data.has(trainId)) {
                error(req.response(), String.format("Train with ID %s was not found", trainId), NOT_FOUND);
                return;
              }


              JsonObject trainData = data.get(trainId).getAsJsonObject();
              JsonArray seats = new JsonParser().parse(seatsJson).getAsJsonArray();

              // Validate the reservation details
              Iterator<JsonElement> seatsIterator = seats.iterator();
              while (seatsIterator.hasNext()) {
                String seat = seatsIterator.next().getAsString();
                if (trainData.getAsJsonObject("seats").has(seat)) {
                  String existingReservation = trainData.getAsJsonObject("seats").getAsJsonObject(seat).get("booking_reference").getAsString();
                  if (!"".equals(existingReservation) && !(existingReservation.equals(bookingRef))) {
                    error(req.response(), String.format("%s on %s is already booked with reference:  %s", seat, trainId, bookingRef), CONFLICT);
                    return;
                  }
                } else {
                  error(req.response(), String.format("seat not found %s", seat), NOT_FOUND);
                  return;
                }
              }

              // Update the reservation data
              seatsIterator = seats.iterator();
              while (seatsIterator.hasNext()) {
                String seat = seatsIterator.next().getAsString();
                trainData.getAsJsonObject("seats").getAsJsonObject(seat).addProperty("booking_reference", bookingRef);
              }

              req.response().end(prettyJsonFrom(data.get(trainId)));
            } catch (IllegalStateException e) {
              // This is a difficult error to diagnose from the client otherwise
              error(req.response(), "No multi-part form attributes supplied in the request body", BAD_REQUEST);
            }
          }
        });
      }
    });

    /*
    Remove all reservations on a particular train. Use it with care:
    http://localhost:8081/reset/express_2000
    */
    rm.get("/reset/:trainId", new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        String trainId = req.params().get("trainId");
        container.logger().warn("Handling a request for /reset/" + trainId);
        req.response().putHeader("Content-Type", "application/json");
        if (data.has(trainId)) {
          JsonObject trainData = data.get(trainId).getAsJsonObject();

          for (Map.Entry<String, JsonElement> entry : trainData.getAsJsonObject("seats").entrySet()) {
            String seat = entry.getKey();
            trainData.getAsJsonObject("seats").getAsJsonObject(seat).addProperty("booking_reference", "");
          }

          req.response().end(prettyJsonFrom(data.get(trainId)));
        } else {
          error(req.response(), String.format("Train with ID %s was not found", trainId), NOT_FOUND);
        }
      }
    });

    vertx.createHttpServer().requestHandler(rm).listen(8081);
  }


  private static boolean emptyOrNull(String... things) {
    for (String thing : things) {
      if (thing == null || thing.trim().equals("")) return true;
    }
    return false;
  }

  private void error(HttpServerResponse resp, String message, int statusCode) {
    container.logger().error("ERROR: "+ message + " [" + statusCode + "]");
    resp.setStatusCode(statusCode);
    JsonObject error = new JsonObject();
    error.addProperty("error", message);
    resp.end(prettyJsonFrom(error));
  }

  private static String prettyJsonFrom(JsonElement data) {
    return new GsonBuilder().setPrettyPrinting().create().toJson(data);
  }

  private JsonObject readTrainData() {
    try {
      JsonParser jsonParser = new JsonParser();
      return jsonParser.parse(new FileReader("src/main/resources/trains.json")).getAsJsonObject();
    } catch (FileNotFoundException e) {
      container.logger().fatal("Couldn't read in JSON data for trains", e);
    }
    return null;
  }
}
