package trains.bookingref

import org.vertx.groovy.platform.Verticle

class BookingReferenceService extends Verticle {
  long CURRENT_ID=1000000

  @Override
  def start() {
    def rm = new org.vertx.groovy.core.http.RouteMatcher()

    rm.get('/booking_reference') {
      req ->
        req.response.putHeader("Content-Type", "text/plain")
        req.response.end "${CURRENT_ID++}"
    }

    vertx.createHttpServer().requestHandler(rm.asClosure()).listen(8082)
  }
}
