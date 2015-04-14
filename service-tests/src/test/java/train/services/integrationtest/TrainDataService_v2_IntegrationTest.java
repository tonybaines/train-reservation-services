package train.services.integrationtest;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.hamcrest.Matcher;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;

public class TrainDataService_v2_IntegrationTest {
  private int bookingRef = 9000;

  @Test
  public void theTrainServiceReturnsJsonReservationInformationAboutATrain() throws Exception {
    HttpResponse<JsonNode> trainDataResponse = Unirest.get("http://127.0.0.1:9081/data_for_train/express_2000").asJson();

    assertThat(trainDataResponse.getStatus(), is(200));
    JsonNode resultJson = trainDataResponse.getBody();
    assertThat(resultJson.getObject().get("seats"), is(notNullValue()));
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
    JSONObject seats = resultJson.getObject().getJSONObject("seats");
    for (Object seat : seats.keySet()) {
      assertThat(seats.getJSONObject((String)seat).getString("booking_reference"), is(""));
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
    assertThat(reservationResponse.getBody().getObject().getJSONObject("seats").getJSONObject(seat).getString("booking_reference"), matcher);
  }

  private static void expectingAnError(HttpResponse<JsonNode> response, int statusCode, Matcher<String> matcher) {
    String errorMessage = response.getBody().getObject().getString("error");
    System.err.println(errorMessage);
    assertThat(response.getStatus(), is(statusCode));
    assertThat(errorMessage, matcher);
  }
}
