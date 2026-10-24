package sa.lendo.credit.analyst.multi_agent.events;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisEventPublisherTest {

    @Test
    void buffersEventsInPublishOrder() {
        AnalysisEventPublisher publisher = new AnalysisEventPublisher();

        publisher.modelCall("credit_analyst", 1);
        publisher.toolResult("call-1", "getCreditScore", "{\"score\":690}");

        assertThat(publisher.events()).hasSize(2);
        assertThat(publisher.events().get(0).type()).isEqualTo("model_call");
        assertThat(publisher.events().get(0).payload().path("agent").asText()).isEqualTo("credit_analyst");
        assertThat(publisher.events().get(1).type()).isEqualTo("tool_result");
    }

    @Test
    void notifiesSubscribersOnPublish() {
        AnalysisEventPublisher publisher = new AnalysisEventPublisher();
        List<AnalysisEvent> received = new ArrayList<>();
        publisher.subscribe(received::add);

        publisher.publish("error", Map.of("message", "boom", "phase", "analysis"));

        assertThat(received).hasSize(1);
        assertThat(received.get(0).type()).isEqualTo("error");
        assertThat(received.get(0).payload().path("message").asText()).isEqualTo("boom");
    }

    @Test
    void subscriberFailureDoesNotBreakPublishing() {
        AnalysisEventPublisher publisher = new AnalysisEventPublisher();
        publisher.subscribe(e -> {
            throw new RuntimeException("dead emitter");
        });

        publisher.runCompleted("COMPLETED", 1234);

        assertThat(publisher.events()).hasSize(1);
    }
}
