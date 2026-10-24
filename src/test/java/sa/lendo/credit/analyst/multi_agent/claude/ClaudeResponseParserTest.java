package sa.lendo.credit.analyst.multi_agent.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import sa.lendo.credit.analyst.multi_agent.claude.ClaudeResponseParser.ParseMode;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeResponseParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> TOOLS = Set.of("getCreditScore", "getFinancials");

    private final ClaudeResponseParser parser = new ClaudeResponseParser();

    /** Builds a CLI result envelope like the real `claude -p --output-format json` output. */
    private static String envelope(String structuredOutputJson) {
        try {
            ObjectNode env = MAPPER.createObjectNode();
            env.put("type", "result");
            env.put("subtype", "success");
            env.put("is_error", false);
            env.put("duration_ms", 9000);
            env.put("num_turns", 3);
            env.put("session_id", "sess-1");
            env.put("stop_reason", "tool_use");
            env.set("usage", MAPPER.createObjectNode()
                    .put("input_tokens", 120).put("output_tokens", 45));
            if (structuredOutputJson != null) {
                JsonNode so = MAPPER.readTree(structuredOutputJson);
                env.set("structured_output", so);
                env.put("result", so.toString());
            } else {
                env.put("result", "plain text answer");
            }
            return env.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void mapsToolCallsToToolExecutionRequests() {
        String stdout = envelope("""
                {"action":"tool_calls","tool_calls":[
                  {"name":"getCreditScore","arguments":{"applicantId":"APP-123"}},
                  {"name":"getFinancials","arguments":{"applicantId":"APP-123"}}]}""");

        Response<AiMessage> response = parser.parse(stdout, ParseMode.WRAPPER, TOOLS);

        assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
        assertThat(response.content().toolExecutionRequests()).hasSize(2);
        assertThat(response.content().toolExecutionRequests().get(0).name()).isEqualTo("getCreditScore");
        assertThat(response.content().toolExecutionRequests().get(0).arguments()).contains("APP-123");
        assertThat(response.content().toolExecutionRequests().get(0).id()).isNotBlank();
    }

    @Test
    void mapsFinalAnswerObjectToJsonText() {
        String stdout = envelope("""
                {"action":"final_answer","final_answer":{"decision":"REJECT","summary":"LTV too high"}}""");

        Response<AiMessage> response = parser.parse(stdout, ParseMode.WRAPPER, TOOLS);

        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.content().text()).contains("\"decision\"").contains("REJECT");
    }

    @Test
    void fallsBackToResultStringWhenStructuredOutputMissing() throws Exception {
        ObjectNode env = (ObjectNode) MAPPER.readTree(envelope(
                "{\"action\":\"final_answer\",\"final_answer\":\"done\"}"));
        env.remove("structured_output");

        Response<AiMessage> response = parser.parse(env.toString(), ParseMode.WRAPPER, TOOLS);

        assertThat(response.content().text()).isEqualTo("done");
    }

    @Test
    void structuredModeReturnsWholeObjectAsText() {
        String stdout = envelope("{\"answer\":\"max LTV is 90%\"}");

        Response<AiMessage> response = parser.parse(stdout, ParseMode.STRUCTURED, Set.of());

        assertThat(response.content().text()).contains("max LTV is 90%");
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void textModeReturnsResultVerbatim() {
        String stdout = envelope(null);

        Response<AiMessage> response = parser.parse(stdout, ParseMode.TEXT, Set.of());

        assertThat(response.content().text()).isEqualTo("plain text answer");
    }

    @Test
    void mapsTokenUsage() {
        String stdout = envelope(null);

        Response<AiMessage> response = parser.parse(stdout, ParseMode.TEXT, Set.of());

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(120);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(45);
    }

    @Test
    void throwsWhenCliReportsError() throws Exception {
        ObjectNode env = (ObjectNode) MAPPER.readTree(envelope(null));
        env.put("is_error", true);
        env.put("result", "Credit balance too low");

        assertThatThrownBy(() -> parser.parse(env.toString(), ParseMode.TEXT, Set.of()))
                .isInstanceOf(ClaudeCliException.class)
                .hasMessageContaining("Credit balance too low");
    }

    @Test
    void throwsOnUnparseableOutput() {
        assertThatThrownBy(() -> parser.parse("not json at all", ParseMode.WRAPPER, TOOLS))
                .isInstanceOf(ClaudeCliException.class);
    }

    @Test
    void throwsWhenModelRequestsUnknownTool() {
        String stdout = envelope("""
                {"action":"tool_calls","tool_calls":[{"name":"launchMissiles","arguments":{}}]}""");

        assertThatThrownBy(() -> parser.parse(stdout, ParseMode.WRAPPER, TOOLS))
                .isInstanceOf(ClaudeCliException.class)
                .hasMessageContaining("launchMissiles");
    }

    @Test
    void throwsOnUnknownAction() {
        String stdout = envelope("{\"action\":\"dance\"}");

        assertThatThrownBy(() -> parser.parse(stdout, ParseMode.WRAPPER, TOOLS))
                .isInstanceOf(ClaudeCliException.class);
    }
}
