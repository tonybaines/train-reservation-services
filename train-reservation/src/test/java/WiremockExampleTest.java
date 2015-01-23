import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Rule;
import org.junit.Test;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

/**
 * See http://wiremock.org/getting-started.html#junit-4-x for more
 */
public class WiremockExampleTest {
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8081); // No-args constructor defaults to port 8080

    @Test
    public void exampleTest() throws UnirestException {
        stubFor(get(urlEqualTo("/my/resource"))
                .withHeader("Accept", equalTo("text/plain"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("Hello World!")));

        HttpResponse<String> response = Unirest.get("http://127.0.0.1:8081/my/resource").header("Accept", "text/plain").asString();

        assertThat(response.getStatus(), is(200));
        assertThat(response.getBody(), is("Hello World!"));

        verify(getRequestedFor(urlMatching("/my/resource"))
                .withRequestBody(matching("")));
    }


}
