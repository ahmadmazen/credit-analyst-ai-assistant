package sa.lendo.credit.analyst.multi_agent.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.junit.jupiter.api.Test;
import sa.lendo.credit.analyst.multi_agent.tools.CreditAnalysisTools;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeSchemaBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static List<ToolSpecification> creditTools() {
        return ToolSpecifications.toolSpecificationsFrom(CreditAnalysisTools.class);
    }

    @Test
    void wrapperSchemaOffersToolCallsOrFinalAnswer() {
        ObjectNode schema = ClaudeSchemaBuilder.wrapperSchema(creditTools(), null);

        List<String> actionValues = new ArrayList<>();
        schema.path("properties").path("action").path("enum").forEach(n -> actionValues.add(n.asText()));
        assertThat(actionValues).containsExactlyInAnyOrder("tool_calls", "final_answer");
        assertThat(schema.path("required").toString()).contains("action");
    }

    @Test
    void wrapperSchemaRestrictsToolNamesToRegisteredTools() {
        ObjectNode schema = ClaudeSchemaBuilder.wrapperSchema(creditTools(), null);

        JsonNode nameEnum = schema.path("properties").path("tool_calls")
                .path("items").path("properties").path("name").path("enum");
        List<String> names = new ArrayList<>();
        nameEnum.forEach(n -> names.add(n.asText()));
        assertThat(names).containsExactlyInAnyOrder(
                "getCustomerData", "getCreditScore", "getFinancials",
                "calculateDtiRatio", "getApplicationDetails");
    }

    @Test
    void finalAnswerDefaultsToStringWhenNoSchemaGiven() {
        ObjectNode schema = ClaudeSchemaBuilder.wrapperSchema(creditTools(), null);

        assertThat(schema.path("properties").path("final_answer").path("type").asText())
                .isEqualTo("string");
    }

    @Test
    void customFinalAnswerSchemaIsEmbedded() throws Exception {
        JsonNode decisionSchema = MAPPER.readTree(
                "{\"type\":\"object\",\"properties\":{\"decision\":{\"type\":\"string\"}}}");

        ObjectNode schema = ClaudeSchemaBuilder.wrapperSchema(creditTools(), decisionSchema);

        assertThat(schema.path("properties").path("final_answer").path("properties")
                .path("decision").path("type").asText()).isEqualTo("string");
    }
}
