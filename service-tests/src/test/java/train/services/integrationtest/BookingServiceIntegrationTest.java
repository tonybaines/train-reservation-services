package train.services.integrationtest;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class BookingServiceIntegrationTest {
  @Test
  public void theBookingReferenceServiceReturnsAnIntegerId() throws Exception {
    HttpResponse<String> bookingRefResponse = Unirest.get("http://127.0.0.1:8082/booking_reference").asString();

    assertThat(bookingRefResponse.getStatus(), is(200));
    Integer.parseInt(bookingRefResponse.getBody());
  }
}
