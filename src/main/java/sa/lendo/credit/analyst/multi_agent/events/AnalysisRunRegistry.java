package sa.lendo.credit.analyst.multi_agent.events;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class AnalysisRunRegistry {

    private final ConcurrentMap<String, AnalysisRun> runs = new ConcurrentHashMap<>();

    public void register(AnalysisRun run) {
        runs.put(run.id(), run);
    }

    public Optional<AnalysisRun> find(String id) {
        return Optional.ofNullable(runs.get(id));
    }
}
