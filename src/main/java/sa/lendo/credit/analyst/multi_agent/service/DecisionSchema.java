package sa.lendo.credit.analyst.multi_agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON schema for the credit analyst's final answer, enforced end-to-end by the
 * Claude CLI's --json-schema flag so the UI can always render a decision card.
 */
public final class DecisionSchema {

    public static final JsonNode NODE = load();

    private DecisionSchema() {
    }

    private static JsonNode load() {
        try {
            return new ObjectMapper().readTree("""
                    {
                      "type": "object",
                      "properties": {
                        "decision": {"type": "string", "enum": ["APPROVE", "REJECT", "REVIEW"]},
                        "summary": {"type": "string"},
                        "reasoningSteps": {
                          "type": "array",
                          "items": {
                            "type": "object",
                            "properties": {
                              "step": {"type": "string"},
                              "ruleCited": {"type": "string"},
                              "outcome": {"type": "string", "enum": ["PASS", "FAIL", "INFO"]}
                            },
                            "required": ["step", "outcome"]
                          }
                        },
                        "metrics": {
                          "type": "object",
                          "properties": {
                            "creditScore": {"type": "number"},
                            "dtiPercent": {"type": "number"},
                            "ltvPercent": {"type": "number"},
                            "annualIncome": {"type": "number"}
                          }
                        },
                        "citedRules": {"type": "array", "items": {"type": "string"}}
                      },
                      "required": ["decision", "summary", "reasoningSteps"]
                    }""");
        } catch (Exception e) {
            throw new IllegalStateException("Invalid decision schema", e);
        }
    }
}
