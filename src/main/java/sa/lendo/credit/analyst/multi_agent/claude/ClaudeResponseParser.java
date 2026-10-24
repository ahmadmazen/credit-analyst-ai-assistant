package sa.lendo.credit.analyst.multi_agent.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Parses the JSON envelope printed by `claude -p --output-format json` and maps it
 * back onto langchain4j's chat-model contract.
 */
public class ClaudeResponseParser {

    public enum ParseMode {
        /** Tools are registered: expect {action, tool_calls, final_answer} wrapper. */
        WRAPPER,
        /** No tools, but a final-answer schema: expect the schema object directly. */
        STRUCTURED,
        /** No schema at all: use the plain result text. */
        TEXT
    }

    private final ObjectMapper mapper = new ObjectMapper();

    public Response<AiMessage> parse(String stdout, ParseMode mode, Set<String> allowedToolNames) {
        JsonNode envelope = readEnvelope(stdout);
        TokenUsage tokenUsage = tokenUsage(envelope);

        if (mode == ParseMode.TEXT) {
            return Response.from(AiMessage.from(envelope.path("result").asText("")),
                    tokenUsage, FinishReason.STOP);
        }

        JsonNode output = structuredOutput(envelope);
        if (mode == ParseMode.STRUCTURED) {
            return Response.from(AiMessage.from(asText(output)), tokenUsage, FinishReason.STOP);
        }

        String action = output.path("action").asText("");
        return switch (action) {
            case "tool_calls" -> Response.from(
                    AiMessage.from(toolRequests(output, allowedToolNames)),
                    tokenUsage, FinishReason.TOOL_EXECUTION);
            case "final_answer" -> {
                JsonNode finalAnswer = output.get("final_answer");
                if (finalAnswer == null || finalAnswer.isNull()) {
                    throw new ClaudeCliException(
                            "claude answered action=final_answer without a final_answer field");
                }
                yield Response.from(AiMessage.from(asText(finalAnswer)), tokenUsage, FinishReason.STOP);
            }
            default -> throw new ClaudeCliException(
                    "claude returned an unknown action '" + action + "' in: " + excerpt(output.toString()));
        };
    }

    private JsonNode readEnvelope(String stdout) {
        JsonNode envelope;
        try {
            envelope = mapper.readTree(stdout);
        } catch (Exception e) {
            throw new ClaudeCliException(
                    "Could not parse claude CLI output as JSON: " + excerpt(stdout), e);
        }
        if (envelope == null || !envelope.isObject() || !"result".equals(envelope.path("type").asText())) {
            throw new ClaudeCliException(
                    "Unexpected claude CLI output (no result envelope): " + excerpt(stdout));
        }
        boolean failed = envelope.path("is_error").asBoolean(false)
                || (envelope.hasNonNull("subtype") && !"success".equals(envelope.path("subtype").asText()));
        if (failed) {
            throw new ClaudeCliException("claude CLI reported an error: "
                    + excerpt(envelope.path("result").asText(envelope.path("subtype").asText(""))));
        }
        return envelope;
    }

    private JsonNode structuredOutput(JsonNode envelope) {
        JsonNode output = envelope.get("structured_output");
        if (output != null && !output.isNull()) {
            return output;
        }
        // some CLI paths omit structured_output; the same JSON is then in `result` as a string
        try {
            return mapper.readTree(envelope.path("result").asText(""));
        } catch (Exception e) {
            throw new ClaudeCliException("claude CLI returned no structured output: "
                    + excerpt(envelope.path("result").asText("")), e);
        }
    }

    private List<ToolExecutionRequest> toolRequests(JsonNode output, Set<String> allowedToolNames) {
        JsonNode calls = output.path("tool_calls");
        if (!calls.isArray() || calls.isEmpty()) {
            throw new ClaudeCliException("claude answered action=tool_calls without any tool_calls");
        }
        List<ToolExecutionRequest> requests = new ArrayList<>();
        for (JsonNode call : calls) {
            String name = call.path("name").asText("");
            if (!allowedToolNames.contains(name)) {
                throw new ClaudeCliException("claude requested unknown tool '" + name
                        + "' (allowed: " + allowedToolNames + ")");
            }
            JsonNode arguments = call.path("arguments");
            requests.add(ToolExecutionRequest.builder()
                    .id("call-" + UUID.randomUUID())
                    .name(name)
                    .arguments(arguments.isObject() ? arguments.toString() : "{}")
                    .build());
        }
        return requests;
    }

    private TokenUsage tokenUsage(JsonNode envelope) {
        JsonNode usage = envelope.get("usage");
        if (usage == null || usage.isNull()) {
            return null;
        }
        return new TokenUsage(
                usage.path("input_tokens").asInt(0),
                usage.path("output_tokens").asInt(0));
    }

    private static String asText(JsonNode node) {
        return node.isTextual() ? node.asText() : node.toString();
    }

    private static String excerpt(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 300 ? text : text.substring(0, 300) + "…";
    }
}
