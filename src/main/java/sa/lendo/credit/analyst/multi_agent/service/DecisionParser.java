package sa.lendo.credit.analyst.multi_agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Parses the credit analyst's final answer (a JSON decision object enforced by
 * --json-schema) leniently: any unparseable text degrades to an UNKNOWN decision
 * carrying the raw text, so the UI always has something to render.
 */
public final class DecisionParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DecisionParser() {
    }

    public static JsonNode parse(String finalAnswer) {
        try {
            JsonNode node = MAPPER.readTree(finalAnswer);
            if (node != null && node.isObject() && node.hasNonNull("decision")) {
                ObjectNode decision = (ObjectNode) node;
                if (!decision.has("reasoningSteps")) {
                    decision.putArray("reasoningSteps");
                }
                return decision;
            }
        } catch (Exception ignored) {
            // fall through to the raw-text fallback
        }
        ObjectNode fallback = MAPPER.createObjectNode();
        fallback.put("decision", "UNKNOWN");
        fallback.put("summary", finalAnswer == null ? "" : finalAnswer);
        fallback.putArray("reasoningSteps");
        return fallback;
    }
}
