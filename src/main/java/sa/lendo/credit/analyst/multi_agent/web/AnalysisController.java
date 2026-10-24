package sa.lendo.credit.analyst.multi_agent.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sa.lendo.credit.analyst.multi_agent.events.AnalysisEvent;
import sa.lendo.credit.analyst.multi_agent.events.AnalysisRun;
import sa.lendo.credit.analyst.multi_agent.events.AnalysisRunRegistry;
import sa.lendo.credit.analyst.multi_agent.service.AnalysisService;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RestController
public class AnalysisController {

    public record AnalysisRequest(String applicantId, String productType) {
    }

    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final AnalysisService analysisService;
    private final AnalysisRunRegistry registry;
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public AnalysisController(AnalysisService analysisService, AnalysisRunRegistry registry) {
        this.analysisService = analysisService;
        this.registry = registry;
    }

    @PostMapping("/api/analysis")
    public ResponseEntity<Map<String, String>> start(@RequestBody AnalysisRequest request) {
        String runId = analysisService.startAnalysis(request.applicantId(), request.productType());
        return ResponseEntity.accepted().body(Map.of("runId", runId));
    }

    @GetMapping("/api/analysis/{id}")
    public Map<String, Object> snapshot(@PathVariable String id) {
        AnalysisRun run = registry.find(id).orElseThrow(() -> new RunNotFoundException(id));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("runId", run.id());
        snapshot.put("status", run.status().name());
        snapshot.put("applicantId", run.applicantId());
        snapshot.put("productType", run.productType());
        snapshot.put("decision", run.decision());
        snapshot.put("events", run.publisher().events());
        return snapshot;
    }

    @GetMapping("/api/analysis/{id}/events")
    public SseEmitter events(@PathVariable String id) {
        AnalysisRun run = registry.find(id).orElseThrow(() -> new RunNotFoundException(id));
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        ScheduledFuture<?> heartbeat = heartbeats.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | RuntimeException ignored) {
                // client went away; onCompletion/onTimeout cancels this task
            }
        }, 15, 15, TimeUnit.SECONDS);
        emitter.onCompletion(() -> heartbeat.cancel(false));
        emitter.onTimeout(() -> heartbeat.cancel(false));

        Consumer<AnalysisEvent> forward = event -> {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.type())
                        .data(event.payload().toString()));
                if ("run_completed".equals(event.type())) {
                    emitter.complete();
                }
            } catch (IOException e) {
                // rethrow as unchecked so the publisher drops this dead subscriber
                throw new RuntimeException(e);
            }
        };
        run.publisher().subscribeWithReplay(forward);
        return emitter;
    }
}
