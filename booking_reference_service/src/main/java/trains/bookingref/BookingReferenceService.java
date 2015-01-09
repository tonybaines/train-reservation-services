package trains.bookingref;

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.platform.Verticle;
import org.vertx.java.core.http.RouteMatcher;

public class BookingReferenceService extends Verticle {

    private long CURRENT_ID = 1000000;

    @Override
    public void start() {
        RouteMatcher rm = new RouteMatcher();
        rm.get("/booking_reference", new Handler<HttpServerRequest>() {
            @Override
            public void handle(HttpServerRequest req) {
                req.response().putHeader("Content-Type", "text/plain");
                req.response().end(Long.toString(CURRENT_ID++));
            }
        });

        vertx.createHttpServer().requestHandler(rm).listen(8082);
    }
}
