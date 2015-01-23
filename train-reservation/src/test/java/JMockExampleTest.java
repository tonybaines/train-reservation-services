import org.jmock.Mockery;
import org.jmock.Expectations;
import org.junit.Test;

/**
 * See http://www.jmock.org/cheat-sheet.html for more
 */
public class JMockExampleTest {
    private Mockery context = new Mockery();
    @Test
    public void anObserver() throws Exception {
        // Given
        final Observer observer = context.mock(Observer.class);

        final Observable observable = new Observable();
        observable.setObserver(observer);

        context.checking(new Expectations() {{
            oneOf(observer).notify(with(equal("TESTING")));
        }});


        // When the observable is updated
        observable.update("TESTING");

        // Then the observer is notified
        context.assertIsSatisfied();
    }

    // Classes under test
    interface Observer {
        void notify(String msg);
    }

    class Observable {
        private Observer observer;
        public void setObserver(Observer observer) { this.observer = observer; }
        public void update(String arg) { notifyObserver(arg); }
        private void notifyObserver(String arg) { observer.notify(arg); }
    }
}
