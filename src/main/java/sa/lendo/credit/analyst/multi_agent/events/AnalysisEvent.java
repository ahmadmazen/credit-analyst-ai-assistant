package sa.lendo.credit.analyst.multi_agent.events;

import com.fasterxml.jackson.databind.JsonNode;

public record AnalysisEvent(String type, long timestamp, JsonNode payload) {
}
