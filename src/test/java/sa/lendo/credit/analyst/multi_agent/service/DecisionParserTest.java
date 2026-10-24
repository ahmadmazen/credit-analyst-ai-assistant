package sa.lendo.credit.analyst.multi_agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionParserTest {

    @Test
    void parsesStructuredDecisionJson() {
        JsonNode decision = DecisionParser.parse("""
                {"decision":"REJECT","summary":"LTV 93.1% exceeds the 90% cap",
                 "reasoningSteps":[{"step":"Checked LTV","ruleCited":"Car loan guide: LTV <= 90%","outcome":"FAIL"}],
                 "metrics":{"ltvPercent":93.1},"citedRules":["LTV <= 90%"]}""");

        assertThat(decision.path("decision").asText()).isEqualTo("REJECT");
        assertThat(decision.path("reasoningSteps")).hasSize(1);
        assertThat(decision.path("metrics").path("ltvPercent").asDouble()).isEqualTo(93.1);
    }

    @Test
    void degradesUnparseableTextToUnknownDecision() {
        JsonNode decision = DecisionParser.parse("The loan should be rejected because...");

        assertThat(decision.path("decision").asText()).isEqualTo("UNKNOWN");
        assertThat(decision.path("summary").asText()).contains("The loan should be rejected");
        assertThat(decision.path("reasoningSteps").isArray()).isTrue();
    }

    @Test
    void degradesJsonWithoutDecisionFieldToUnknown() {
        JsonNode decision = DecisionParser.parse("{\"foo\":1}");

        assertThat(decision.path("decision").asText()).isEqualTo("UNKNOWN");
    }
}
