package train.services.integrationtest;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.hamcrest.Matcher;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class TrainDataService_v1_IntegrationTest {
  private int bookingRef = 9000;

  @Test
  public void theTrainServiceReturnsJsonReservationInformationAboutATrain() throws Exception {
    HttpResponse<JsonNode> trainDataResponse = Unirest.get("http://127.0.0.1:9081/data_for_train/express_2000").asJson();

    assertThat(trainDataResponse.getStatus(), is(200));
    JsonNode resultJson = trainDataResponse.getBody();
    assertThat(resultJson.getObject().get("coaches"), is(notNullValue()));
  }

  @Test
  public void theTrainServiceReturnsAFailureIfTheTrainIdIsNotRecognised() throws Exception {
    HttpResponse<JsonNode> trainDataResponse = Unirest.get("http://127.0.0.1:9081/data_for_train/UNKNOWN").asJson();
    expectingAnError(trainDataResponse, 404, is("Train with ID UNKNOWN was not found"));
  }

  @Test
  public void requestingAReservationWithNoAttributesGivesAnError() throws Exception {
    HttpResponse<JsonNode> reservationResponse = Unirest.post("http://127.0.0.1:9081/reserve").asJson();
    expectingAnError(reservationResponse, 400, startsWith("No multi-part form attributes supplied in the request body"));
  }

  @Test
  public void requestingAReservationWithoutValidParametersGivesAnError() throws Exception {
    HttpResponse<JsonNode> reservationResponse = Unirest.post("http://127.0.0.1:9081/reserve").fields(new HashMap<String, Object>()).asJson();
    expectingAnError(reservationResponse, 400, startsWith("One or more request attributes missing"));
  }

  @Test
  public void requestingAReservationForAnUnknownTrainIdGivesAnError() throws Exception {
    HttpResponse<JsonNode> reservationResponse = requestReservation(nextBookingRef(), "UNKNOWN", "1A", "2B");
    expectingAnError(reservationResponse, 404, is("Train with ID UNKNOWN was not found"));
  }

  @Test
  public void seatsCanBeReserved() throws Exception {
    final String bookingRef = nextBookingRef();
    HttpResponse<JsonNode> reservationResponse = requestReservation(bookingRef, "express_2000", "1A", "2B");

    assertThat(reservationResponse.getStatus(), is(200));
    expectSeatToBeReserved(reservationResponse, "1A", is(bookingRef));
    expectSeatToBeReserved(reservationResponse, "2B", is(bookingRef));
  }

  @Test
  public void reservationRequestsAreIdempotentAndCanBeRepeated() throws Exception {
    final String bookingRef = nextBookingRef();
    requestReservation(bookingRef, "express_2000", "1A", "2B");
    HttpResponse<JsonNode> reservationResponse = requestReservation(bookingRef, "express_2000", "1A", "2B");

    assertThat(reservationResponse.getStatus(), is(200));
    expectSeatToBeReserved(reservationResponse, "1A", is(bookingRef));
    expectSeatToBeReserved(reservationResponse, "2B", is(bookingRef));
  }

  @Test
  public void attemptingADuplicateReservationWithADifferentBookingReferenceIsAnError() throws Exception {
    requestReservation(nextBookingRef(), "express_2000", "1A", "2B");
    HttpResponse<JsonNode> reservationResponse = requestReservation(nextBookingRef(), "express_2000", "2B");
    expectingAnError(reservationResponse, 409, startsWith("2B on express_2000 is already booked"));
  }

  @Test
  public void attemptingAReservationWithInvalidSeatsIsAnError() throws Exception {
    HttpResponse<JsonNode> reservationResponse = requestReservation("1", "express_2000", "1D");
    expectingAnError(reservationResponse, 404, is("seat not found 1D"));
  }

  @Test
  public void allReservationsCanBeCleared() throws Exception {
    // Given a reservation
    requestReservation(nextBookingRef(), "express_2000", "1A", "2B");

    // When all reservations are reset
    HttpResponse<JsonNode> trainDataResponse = Unirest.get("http://127.0.0.1:9081/reset/express_2000").asJson();

    assertThat(trainDataResponse.getStatus(), is(200));
    expectNoReservations(trainDataResponse);
  }

  private static void expectNoReservations(HttpResponse<JsonNode> response) {
    JsonNode resultJson = response.getBody();
    JSONArray coachesData = resultJson.getObject().getJSONArray("coaches");

    for(int i=0; i< coachesData.length(); i++) {
      JSONObject coach =  coachesData.getJSONObject(i);
        JSONArray seatsData = coach.getJSONArray("seats");
        for (int j = 0; j < seatsData.length(); j++) {
          assertThat(seatsData.getJSONObject(j).getString("booking_reference"), is(""));
        }
    }
  }

  private String nextBookingRef() {
    return Integer.toString(bookingRef++);
  }

  private static HttpResponse<JsonNode> requestReservation(final String bookingRef, final String trainId, String... seats) throws UnirestException {
    final StringBuilder seatsBuilder = new StringBuilder("[");
    for (String seat : seats) {
      seatsBuilder.append("\"" + seat + "\",");
    }
    seatsBuilder.deleteCharAt(seatsBuilder.length()-1);
    seatsBuilder.append("]");

    final Map<String, Object> params = new HashMap<String, Object>() {{
      put("train_id", trainId);
      put("seats", seatsBuilder.toString());
      put("booking_reference", bookingRef);
    }};
    return Unirest.post("http://127.0.0.1:9081/reserve").fields(params).asJson();
  }


  private static void expectSeatToBeReserved(HttpResponse<JsonNode> reservationResponse, String seat, Matcher<String >matcher) {
    assertThat(findSeat(reservationResponse.getBody().getObject(), seat).getString("booking_reference"), matcher);
  }

  private static void expectingAnError(HttpResponse<JsonNode> response, int statusCode, Matcher<String> matcher) {
    String errorMessage = response.getBody().getObject().getString("error");
    System.err.println(errorMessage);
    assertThat(response.getStatus(), is(statusCode));
    assertThat(errorMessage, matcher);
  }
  private static String seatNumFrom(String seat) {
    return seat.replaceAll("[A-Z]+", "");
  }

  private static String coachFrom(String seat) {
    return seat.replaceAll("\\d+", "");
  }

  private static JSONObject findSeat(JSONObject trainData, String requestedSeat) {
    String coachId = coachFrom(requestedSeat);
    String seatNum = seatNumFrom(requestedSeat);
    JSONArray coachesData = trainData.getJSONArray("coaches");

    for(int i=0; i< coachesData.length(); i++) {
      JSONObject coach =  coachesData.getJSONObject(i);
      if (coach.getString("coach").equals(coachId)) {
        JSONArray seatsData = coach.getJSONArray("seats");
        for (int j = 0; j < seatsData.length(); j++) {
          JSONObject seat = seatsData.getJSONObject(j);
          if (seat.getString("seat_number").equals(seatNum)) {
            return seat;
          }
        }
      }
    }
    throw new IllegalArgumentException(String.format("seat not found %s", requestedSeat));
  }


}
