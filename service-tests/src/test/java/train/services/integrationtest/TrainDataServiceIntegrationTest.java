package train.services.integrationtest;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import org.hamcrest.Matcher;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;

public class TrainDataServiceIntegrationTest {
  @Test
  public void theTrainServiceReturnsJsonReservationInformationAboutATrain() throws Exception {
    HttpResponse<JsonNode> trainDataResponse = Unirest.get("http://127.0.0.1:8081/data_for_train/express_2000").asJson();

    assertThat(trainDataResponse.getStatus(), is(200));
    JsonNode resultJson = trainDataResponse.getBody();
    assertThat(resultJson.getObject().get("seats"), is(notNullValue()));
  }

  @Test
  public void theTrainServiceReturnsAFailureIfTheTrainIdIsNotRecognised() throws Exception {
    HttpResponse<JsonNode> trainDataResponse = Unirest.get("http://127.0.0.1:8081/data_for_train/UNKNOWN").asJson();
    expectingAnError(trainDataResponse, 404, is("Train with ID UNKNOWN was not found"));
  }

  @Test
  public void requestingAReservationWithoutValidParametersGivesAnError() throws Exception {
    Map<String, Object> params = new HashMap<String, Object>();
    HttpResponse<JsonNode> reservationResponse = Unirest.post("http://127.0.0.1:8081/reserve").fields(params).asJson();
    expectingAnError(reservationResponse, 400, startsWith("One or more request attributes missing"));
  }

  @Test
  public void requestingAReservationForAnUnknownTrainIdGivesAnError() throws Exception {
    final Map<String, Object> params = new HashMap<String, Object>() {{
      put("train_id", "UNKNOWN");
      put("seats", "[\"1A\", \"2A\"]");
      put("booking_reference", "1");
    }};
    HttpResponse<JsonNode> reservationResponse = Unirest.post("http://127.0.0.1:8081/reserve").fields(params).asJson();
    expectingAnError(reservationResponse, 404, is("Train with ID UNKNOWN was not found"));
  }

  @Test @Ignore
  public void attemptingADuplicateReservationWithADifferentBookingReferenceIsAnError() throws Exception {

  }

  @Test
  public void attemptingAReservationWithInvalidSeatsIsAnError() throws Exception {
    final Map<String, Object> params = new HashMap<String, Object>() {{
      put("train_id", "express_2000");
      put("seats", "[\"1D\"]");
      put("booking_reference", "1");
    }};
    HttpResponse<JsonNode> reservationResponse = Unirest.post("http://127.0.0.1:8081/reserve").fields(params).asJson();
    expectingAnError(reservationResponse, 404, is("seat not found 1D"));
  }




  private static void expectingAnError(HttpResponse<JsonNode> response, int statusCode, Matcher<String> matcher) {
    String errorMessage = response.getBody().getObject().getString("error");
    System.err.println(errorMessage);
    assertThat(response.getStatus(), is(statusCode));
    assertThat(errorMessage, matcher);
  }
}
