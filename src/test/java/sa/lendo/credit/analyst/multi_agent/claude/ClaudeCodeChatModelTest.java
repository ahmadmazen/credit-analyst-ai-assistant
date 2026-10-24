package sa.lendo.credit.analyst.multi_agent.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import sa.lendo.credit.analyst.multi_agent.events.AnalysisEvent;
import sa.lendo.credit.analyst.multi_agent.events.AnalysisEventPublisher;
import sa.lendo.credit.analyst.multi_agent.tools.CreditAnalysisTools;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeCodeChatModelTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static class FakeRunner implements CommandRunner {
        final List<List<String>> commands = new ArrayList<>();
        final List<String> stdins = new ArrayList<>();
        final Deque<CommandResult> results = new ArrayDeque<>();

        @Override
        public CommandResult run(List<String> command, String stdin, Duration timeout) {
            commands.add(command);
            stdins.add(stdin);
            if (results.isEmpty()) {
                throw new IllegalStateException("no canned result queued");
            }
            return results.pop();
        }
    }

    private static String envelope(String structuredOutputJson) {
        try {
            ObjectNode env = MAPPER.createObjectNode();
            env.put("type", "result").put("subtype", "success").put("is_error", false);
            env.put("duration_ms", 5000).put("num_turns", 3).put("session_id", "s");
            env.set("usage", MAPPER.createObjectNode().put("input_tokens", 10).put("output_tokens", 5));
            if (structuredOutputJson != null) {
                JsonNode so = MAPPER.readTree(structuredOutputJson);
                env.set("structured_output", so);
                env.put("result", so.toString());
            } else {
                env.put("result", "text answer");
            }
            return env.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<ToolSpecification> creditTools() {
        return ToolSpecifications.toolSpecificationsFrom(CreditAnalysisTools.class);
    }

    private static ClaudeCliProperties props() {
        ClaudeCliProperties p = new ClaudeCliProperties();
        p.setPath("claude");
        p.setModel("sonnet");
        p.setTimeoutSeconds(180);
        p.setMaxTurns(16);
        return p;
    }

    private static ClaudeCodeChatModel model(FakeRunner runner, AnalysisEventPublisher publisher) {
        return ClaudeCodeChatModel.builder()
                .commandRunner(runner)
                .properties(props())
                .agentName("credit_analyst")
                .eventPublisher(publisher)
                .build();
    }

    @Test
    void buildsHeadlessCliCommandWithSchemaWhenToolsRegistered() {
        FakeRunner runner = new FakeRunner();
        runner.results.add(new CommandResult(0, envelope(
                "{\"action\":\"final_answer\",\"final_answer\":\"ok\"}"), ""));
        ClaudeCodeChatModel model = model(runner, null);
        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are a credit analyst."),
                UserMessage.from("Analyze APP-123"));

        model.generate(messages, creditTools());

        List<String> cmd = runner.commands.get(0);
        assertThat(cmd).startsWith("claude", "-p");
        assertThat(cmd).containsSequence("--output-format", "json");
        assertThat(cmd).containsSequence("--model", "sonnet");
        assertThat(cmd).containsSequence("--max-turns", "16");
        assertThat(cmd).containsSequence("--tools", "");
        assertThat(cmd).contains("--strict-mcp-config", "--no-session-persistence", "--json-schema");
        // --json-schema must never be combined with --max-turns 1 (schema consumes extra turns)
        assertThat(cmd).doesNotContainSequence("--max-turns", "1");
        // system prompt goes as an argument; conversation goes via stdin
        int sysIdx = cmd.indexOf("--system-prompt");
        assertThat(sysIdx).isGreaterThan(-1);
        assertThat(cmd.get(sysIdx + 1)).contains("You are a credit analyst.");
        assertThat(runner.stdins.get(0)).contains("Analyze APP-123");
    }

    @Test
    void returnsToolExecutionRequestsWhenModelAsksForTools() {
        FakeRunner runner = new FakeRunner();
        runner.results.add(new CommandResult(0, envelope("""
                {"action":"tool_calls","tool_calls":[
                  {"name":"getCreditScore","arguments":{"applicantId":"APP-123"}}]}"""), ""));
        ClaudeCodeChatModel model = model(runner, null);

        Response<AiMessage> response = model.generate(
                List.of(UserMessage.from("Analyze APP-123")), creditTools());

        assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
        assertThat(response.content().toolExecutionRequests()).hasSize(1);
        assertThat(response.content().toolExecutionRequests().get(0).name()).isEqualTo("getCreditScore");
    }

    @Test
    void noToolsAndNoSchemaRunsInPlainTextMode() {
        FakeRunner runner = new FakeRunner();
        runner.results.add(new CommandResult(0, envelope(null), ""));
        ClaudeCodeChatModel model = model(runner, null);

        Response<AiMessage> response = model.generate(List.of(UserMessage.from("Summarize the doc")));

        assertThat(runner.commands.get(0)).doesNotContain("--json-schema");
        assertThat(response.content().text()).isEqualTo("text answer");
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void nonZeroExitThrowsWithStderrExcerpt() {
        FakeRunner runner = new FakeRunner();
        runner.results.add(new CommandResult(1, "", "not authenticated: please run claude login"));
        ClaudeCodeChatModel model = model(runner, null);

        assertThatThrownBy(() -> model.generate(List.of(UserMessage.from("hi")), creditTools()))
                .isInstanceOf(ClaudeCliException.class)
                .hasMessageContaining("not authenticated");
    }

    @Test
    void publishesModelAndToolEventsInOrder() {
        FakeRunner runner = new FakeRunner();
        runner.results.add(new CommandResult(0, envelope("""
                {"action":"tool_calls","tool_calls":[
                  {"name":"getCreditScore","arguments":{"applicantId":"APP-123"}}]}"""), ""));
        runner.results.add(new CommandResult(0, envelope(
                "{\"action\":\"final_answer\",\"final_answer\":\"done\"}"), ""));
        AnalysisEventPublisher publisher = new AnalysisEventPublisher();
        ClaudeCodeChatModel model = model(runner, publisher);
        List<ToolSpecification> tools = creditTools();

        // turn 1: model asks for a tool
        Response<AiMessage> first = model.generate(List.of(UserMessage.from("Analyze APP-123")), tools);
        ToolExecutionRequest request = first.content().toolExecutionRequests().get(0);

        // turn 2: conversation now carries the tool result
        model.generate(List.of(
                UserMessage.from("Analyze APP-123"),
                first.content(),
                ToolExecutionResultMessage.from(request, "{ \"score\": 690 }")), tools);

        List<String> types = publisher.events().stream().map(AnalysisEvent::type).toList();
        assertThat(types).containsExactly(
                "model_call", "model_completed", "tool_call",   // turn 1
                "tool_result", "model_call", "model_completed"); // turn 2
        AnalysisEvent toolCall = publisher.events().get(2);
        assertThat(toolCall.payload().path("name").asText()).isEqualTo("getCreditScore");
        AnalysisEvent toolResult = publisher.events().get(3);
        assertThat(toolResult.payload().path("result").asText()).contains("690");
        assertThat(publisher.events().get(0).payload().path("agent").asText())
                .isEqualTo("credit_analyst");
    }
}
