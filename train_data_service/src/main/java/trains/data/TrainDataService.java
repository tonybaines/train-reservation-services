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
        req.response().putHeader("Content-Type", "application/json");
        if (data.has(trainId)) {
          req.response().end(prettyJsonFrom(data.get(trainId)));
        } else {
          error(req.response(), String.format("Train with ID %s was not found", trainId), NOT_FOUND);
        }
      }
    });


    rm.post("/reserve", new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        log("Got a POST to reserve");
        req.expectMultiPart(true);
        req.endHandler(new VoidHandler() {
          public void handle() {
            final MultiMap attrs = req.formAttributes();
            final String trainId = attrs.get("train_id");
            final String seatsJson = attrs.get("seats");
            final String bookingRef = attrs.get("booking_reference");

            req.response().putHeader("Content-Type", "application/json");

            // Validate the request
            if (emptyOrNull(trainId, seatsJson, bookingRef)) {
              error(req.response(), String.format("One or more request attributes missing: [train_id=%s, seats=%s, booking_reference=%s]", trainId, seatsJson, bookingRef), BAD_REQUEST);
            } else if (!data.has(trainId)) {
              error(req.response(), String.format("Train with ID %s was not found", trainId), NOT_FOUND);
            }


            JsonObject trainData = data.get(trainId).getAsJsonObject();
            JsonArray seats = new JsonParser().parse(seatsJson).getAsJsonArray();

            // Validate the reservation details
            while (seats.iterator().hasNext()) {
              String seat = seats.iterator().next().getAsString();
              if (trainData.getAsJsonObject("seats").has(seat)) {
                String existingReservation = trainData.getAsJsonObject("seats").getAsJsonObject(seat).get("booking_reference").getAsString();
                if (existingReservation != "" && !(existingReservation.equals(bookingRef))) {
                  error(req.response(), String.format("%s on %s already booked with reference:  %s", seat, trainId, bookingRef), CONFLICT);
                  return;
                }
              } else {
                error(req.response(), String.format("seat not found %s", seat), NOT_FOUND);
                return;
              }
            }

            // Update the reservation data

          }
        });


      }
    });
    //      HttpServerRequest req ->
    //
    //          // Update the reservation data
    //          seats.each { data[trainId]["seats"][it]["booking_reference"] = bookingRef }
    //
    //          req.response.end prettyJsonFrom(data[trainId])
    //        }
    //    }

    vertx.createHttpServer().requestHandler(rm).listen(8081);
  }
  //
  //  @Override
  //  def start() {
  //
  //    /*
  //     To reserve seats on a train, you'll need to make a POST request to this url:
  //     http://localhost:8081/reserve
  //     and attach form data for which seats to reserve. There should be three fields:
  //     "train_id", "seats", "booking_reference"
  //     The "seats" field should be a json encoded list of seat ids, for example:
  //     '["1A", "2A"]'
  //     The other two fields are ordinary strings. Note the server will prevent you
  //     from booking a seat that is already reserved with another booking reference.
  //     */
  //    rm.post('/reserve') {
  //      HttpServerRequest req ->
  //        // Handle a multipart form submission
  //        req.expectMultiPart = true
  //        req.endHandler {
  //          def attrs = req.formAttributes
  //          String trainId = attrs['train_id']
  //          String seatsJson = attrs['seats']
  //          String bookingRef = attrs['booking_reference']
  //
  //          req.response.putHeader("Content-Type", "application/json")
  //
  //          // Validate the request
  //          if (emptyOrNull(trainId, seatsJson, bookingRef)) {
  //            jsonError(req.response,
  //              "One or more request attributes missing: [train_id='$trainId', seats='$seatsJson', booking_reference='$bookingRef']",
  //              BAD_REQUEST)
  //            return
  //          } else if (!data.containsKey(trainId)) {
  //            jsonError(req.response, "train_id '$trainId' Not Found", NOT_FOUND)
  //            return
  //          }
  //
  //
  //          def dataForTrain = data[trainId]
  //          def seats = jsonSlurper.parseText(seatsJson)
  //
  //          // Validate the reservation details
  //          for (String seat : seats) {
  //            if (dataForTrain['seats'].containsKey(seat)) {
  //              def existingReservation = dataForTrain["seats"][seat]["booking_reference"]
  //              if (existingReservation != '' && existingReservation != bookingRef) {
  //                jsonError(req.response, "$seat on $trainId already booked with reference:  ${bookingRef}", CONFLICT)
  //                return
  //              }
  //            } else {
  //              jsonError(req.response, "seat not found ${seat}", NOT_FOUND)
  //              return
  //            }
  //          }
  //
  //          // Update the reservation data
  //          seats.each { data[trainId]["seats"][it]["booking_reference"] = bookingRef }
  //
  //          req.response.end prettyJsonFrom(data[trainId])
  //        }
  //    }
  //
  //    /*
  //     Remove all reservations on a particular train. Use it with care:
  //     http://localhost:8081/reset/express_2000
  //     */
  //    rm.get("/reset/:trainId") {
  //      HttpServerRequest req ->
  //        req.response.putHeader("Content-Type", "application/json")
  //        def trainId = req.params.get('trainId')
  //        if (data.containsKey(trainId)) {
  //          for (String seat : data[trainId]["seats"].keySet()) {
  //            data[trainId]["seats"][seat]["booking_reference"] = ''
  //          }
  //          req.response.end JsonOutput.prettyPrint(JsonOutput.toJson(data[trainId]))
  //        } else {
  //          jsonError(req.response, "train_id '$trainId' Not Found", NOT_FOUND)
  //        }
  //    }
  //
  //    vertx.createHttpServer().requestHandler(rm.asClosure()).listen(8081)
  //  }
  //
  //  static def emptyOrNull(String... things) { things.any { it == null || it.trim() == '' } }
  //
  //  static def prettyJsonFrom(data) { JsonOutput.prettyPrint(JsonOutput.toJson(data)) }
  //
  //  static def jsonError(HttpServerResponse resp, String message, int statusCode) {
  //    resp.statusCode = statusCode
  //    resp.end prettyJsonFrom(['error': message])
  //  }


  private static boolean emptyOrNull(String... things) {
    for (String thing : things) {
      if (thing == null || thing.trim().equals("")) return true;
    }
    return false;
  }

  private static void error(HttpServerResponse resp, String message, int statusCode) {
    resp.setStatusCode(statusCode);
    JsonObject error = new JsonObject();
    error.addProperty("error", message);
    resp.end(prettyJsonFrom(error));
  }

  private static String prettyJsonFrom(JsonElement data) {
    return new GsonBuilder().setPrettyPrinting().create().toJson(data);
  }

  private static JsonObject readTrainData() {
    try {

      JsonParser jsonParser = new JsonParser();
      return jsonParser.parse(new FileReader("src/main/resources/trains.json")).getAsJsonObject();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    return null;
  }

  private static void log(String message) {
    System.out.println(message);
  }
}
