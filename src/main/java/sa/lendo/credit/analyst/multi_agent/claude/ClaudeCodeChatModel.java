package sa.lendo.credit.analyst.multi_agent.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import sa.lendo.credit.analyst.multi_agent.claude.ClaudeResponseParser.ParseMode;
import sa.lendo.credit.analyst.multi_agent.events.AnalysisEventPublisher;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * langchain4j ChatLanguageModel backed by the Claude Code CLI in headless mode
 * (`claude -p`). Tool calling is emulated: the CLI is forced (via --json-schema)
 * to answer with either tool-call requests or the final answer, which this class
 * maps back onto langchain4j's function-calling contract.
 */
public class ClaudeCodeChatModel implements ChatLanguageModel {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CommandRunner commandRunner;
    private final ClaudeCliProperties properties;
    private final String agentName;
    private final JsonNode finalAnswerSchema;
    private final AnalysisEventPublisher eventPublisher;
    private final ClaudeResponseParser parser = new ClaudeResponseParser();
    private final Set<String> reportedToolResultIds = ConcurrentHashMap.newKeySet();
    private final AtomicInteger turnCounter = new AtomicInteger();

    private ClaudeCodeChatModel(Builder builder) {
        this.commandRunner = Objects.requireNonNull(builder.commandRunner, "commandRunner");
        this.properties = Objects.requireNonNull(builder.properties, "properties");
        this.agentName = builder.agentName != null ? builder.agentName : "agent";
        this.finalAnswerSchema = builder.finalAnswerSchema;
        this.eventPublisher = builder.eventPublisher;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return doGenerate(messages, List.of());
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return doGenerate(messages, toolSpecifications == null ? List.of() : toolSpecifications);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return doGenerate(messages, List.of(toolSpecification));
    }

    private Response<AiMessage> doGenerate(List<ChatMessage> messages, List<ToolSpecification> tools) {
        publishNewToolResults(messages);
        int turn = turnCounter.incrementAndGet();
        if (eventPublisher != null) {
            eventPublisher.modelCall(agentName, turn);
        }

        ParseMode mode = tools.isEmpty()
                ? (finalAnswerSchema != null ? ParseMode.STRUCTURED : ParseMode.TEXT)
                : ParseMode.WRAPPER;
        JsonNode schema = switch (mode) {
            case WRAPPER -> ClaudeSchemaBuilder.wrapperSchema(tools, finalAnswerSchema);
            case STRUCTURED -> finalAnswerSchema;
            case TEXT -> null;
        };

        String systemPrompt = ClaudePromptSerializer.systemPrompt(messages, tools);
        String transcript = ClaudePromptSerializer.transcript(messages);
        List<String> command = buildCommand(systemPrompt, schema);

        long start = System.nanoTime();
        CommandResult result = commandRunner.run(
                command, transcript, Duration.ofSeconds(properties.getTimeoutSeconds()));
        if (result.exitCode() != 0) {
            throw new ClaudeCliException("claude CLI exited with code " + result.exitCode()
                    + ": " + excerpt(result.stderr().isBlank() ? result.stdout() : result.stderr()));
        }

        Set<String> toolNames = tools.stream().map(ToolSpecification::name).collect(Collectors.toSet());
        Response<AiMessage> response = parser.parse(result.stdout(), mode, toolNames);

        if (eventPublisher != null) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            boolean wantsTools = response.content().hasToolExecutionRequests();
            eventPublisher.modelCompleted(agentName, turn, durationMs,
                    wantsTools ? "tool_calls" : "final_answer");
            if (wantsTools) {
                for (ToolExecutionRequest request : response.content().toolExecutionRequests()) {
                    eventPublisher.toolCall(request.id(), request.name(), parseArguments(request.arguments()));
                }
            }
        }
        return response;
    }

    private void publishNewToolResults(List<ChatMessage> messages) {
        if (eventPublisher == null) {
            return;
        }
        for (ChatMessage message : messages) {
            if (message instanceof ToolExecutionResultMessage result
                    && result.id() != null
                    && reportedToolResultIds.add(result.id())) {
                eventPublisher.toolResult(result.id(), result.toolName(), result.text());
            }
        }
    }

    private List<String> buildCommand(String systemPrompt, JsonNode schema) {
        List<String> command = new ArrayList<>(List.of(
                properties.getPath(), "-p",
                "--output-format", "json",
                "--model", properties.getModel(),
                "--max-turns", String.valueOf(properties.getMaxTurns()),
                "--tools", "",
                "--strict-mcp-config",
                "--no-session-persistence",
                "--disable-slash-commands",
                "--system-prompt", systemPrompt));
        if (schema != null) {
            command.add("--json-schema");
            command.add(schema.toString());
        }
        return command;
    }

    private static JsonNode parseArguments(String arguments) {
        try {
            return MAPPER.readTree(arguments == null ? "{}" : arguments);
        } catch (Exception e) {
            return MAPPER.getNodeFactory().textNode(arguments);
        }
    }

    private static String excerpt(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300) + "…";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private CommandRunner commandRunner;
        private ClaudeCliProperties properties;
        private String agentName;
        private JsonNode finalAnswerSchema;
        private AnalysisEventPublisher eventPublisher;

        public Builder commandRunner(CommandRunner commandRunner) {
            this.commandRunner = commandRunner;
            return this;
        }

        public Builder properties(ClaudeCliProperties properties) {
            this.properties = properties;
            return this;
        }

        public Builder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }

        public Builder finalAnswerSchema(JsonNode finalAnswerSchema) {
            this.finalAnswerSchema = finalAnswerSchema;
            return this;
        }

        public Builder eventPublisher(AnalysisEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
            return this;
        }

        public ClaudeCodeChatModel build() {
            return new ClaudeCodeChatModel(this);
        }
    }
}
