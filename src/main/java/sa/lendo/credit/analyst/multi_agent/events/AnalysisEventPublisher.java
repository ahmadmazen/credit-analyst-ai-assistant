package sa.lendo.credit.analyst.multi_agent.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Per-analysis-run event channel. Events are buffered (so late SSE subscribers can
 * replay the full history) and fanned out to live subscribers.
 */
public class AnalysisEventPublisher {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<AnalysisEvent> events = new ArrayList<>();
    private final List<Consumer<AnalysisEvent>> subscribers = new CopyOnWriteArrayList<>();

    public void publish(String type, Object payload) {
        AnalysisEvent event = new AnalysisEvent(type, System.currentTimeMillis(), mapper.valueToTree(payload));
        // append + fan-out under one lock so subscribeWithReplay can never miss or
        // double-deliver an event published concurrently
        synchronized (events) {
            events.add(event);
            for (Consumer<AnalysisEvent> subscriber : subscribers) {
                deliver(subscriber, event);
            }
        }
    }

    public List<AnalysisEvent> events() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    public void subscribe(Consumer<AnalysisEvent> listener) {
        subscribers.add(listener);
    }

    /** Atomically replays all buffered events to the listener, then subscribes it. */
    public void subscribeWithReplay(Consumer<AnalysisEvent> listener) {
        synchronized (events) {
            for (AnalysisEvent event : events) {
                deliver(listener, event);
            }
            subscribers.add(listener);
        }
    }

    private static void deliver(Consumer<AnalysisEvent> subscriber, AnalysisEvent event) {
        try {
            subscriber.accept(event);
        } catch (RuntimeException ignored) {
            // a broken subscriber (e.g. closed SSE connection) must not break the run
        }
    }

    public void runStarted(String runId, String applicantId, String applicantName, String productType) {
        publish("run_started", Map.of(
                "runId", runId,
                "applicantId", applicantId,
                "applicantName", applicantName,
                "productType", productType));
    }

    public void modelCall(String agent, int turn) {
        publish("model_call", Map.of("agent", agent, "turn", turn));
    }

    public void modelCompleted(String agent, int turn, long durationMs, String action) {
        publish("model_completed", Map.of(
                "agent", agent, "turn", turn, "durationMs", durationMs, "action", action));
    }

    public void toolCall(String id, String name, JsonNode arguments) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", id);
        payload.put("name", name);
        payload.put("arguments", arguments);
        publish("tool_call", payload);
    }

    public void toolResult(String id, String name, String result) {
        publish("tool_result", Map.of("id", id, "name", name, "result", result));
    }

    public void guidelineLookup(String productType, String question, String file) {
        publish("guideline_lookup", Map.of(
                "productType", productType, "question", question, "file", file));
    }

    public void decision(JsonNode decision) {
        publish("decision", decision);
    }

    public void runCompleted(String status, long durationMs) {
        publish("run_completed", Map.of("status", status, "durationMs", durationMs));
    }

    public void error(String message, String phase) {
        publish("error", Map.of("message", message, "phase", phase));
    }
}
