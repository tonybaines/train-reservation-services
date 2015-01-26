import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * See the Unirest Java site for more http://unirest.io/java.html
 */
public class UnirestExamples {
    public static void main(String[] args) {
        try {
            // A simple GET request
            HttpResponse<String> response = Unirest.get("http://www.intra.bt.com").asString();
            response.getStatus(); // e.g. 200, 401
            response.getStatusText(); // e.g. OK, Unauthorized
            response.getBody();


            // Getting JSON responses
            HttpResponse<JsonNode> jsonResponse = Unirest.get("http://127.0.0.1:9081/my-restful-service/resource/1").asJson();
            jsonResponse.getStatus();
            jsonResponse.getStatusText();
            // ... working with JSON
            JsonNode jsonNode = jsonResponse.getBody();
            JSONArray array = jsonNode.getArray();// list of things
            JSONObject object = jsonNode.getObject();// map/object of things


        } catch (UnirestException e) {
            e.printStackTrace();
        }

    }
}
