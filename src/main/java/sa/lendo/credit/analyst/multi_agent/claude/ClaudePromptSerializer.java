package sa.lendo.credit.analyst.multi_agent.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.List;

/**
 * Turns a langchain4j conversation into the system prompt and stdin transcript
 * for a headless `claude -p` invocation.
 */
public final class ClaudePromptSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String EMULATION_PREAMBLE = """

            ---
            You are the reasoning engine of an agent framework. You cannot execute tools \
            yourself; instead you REQUEST them. Based on the conversation transcript you \
            receive, respond with the structured output: either request tool calls \
            (action="tool_calls") using ONLY the tools listed below, with arguments \
            matching each tool's parameter schema exactly — or give the final answer \
            (action="final_answer") once you have everything you need.

            Available tools:
            """;

    private ClaudePromptSerializer() {
    }

    public static String systemPrompt(List<ChatMessage> messages, List<ToolSpecification> tools) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage systemMessage) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(systemMessage.text());
            }
        }
        if (!tools.isEmpty()) {
            sb.append(EMULATION_PREAMBLE);
            for (ToolSpecification tool : tools) {
                sb.append("\n- name: ").append(tool.name());
                if (tool.description() != null) {
                    sb.append("\n  description: ").append(tool.description());
                }
                sb.append("\n  parameters: ").append(parametersJson(tool));
            }
        }
        return sb.toString();
    }

    private static String parametersJson(ToolSpecification tool) {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");
        ArrayNode required = params.putArray("required");
        if (tool.parameters() != null) {
            if (tool.parameters().properties() != null) {
                tool.parameters().properties().forEach((paramName, schema) ->
                        props.set(paramName, MAPPER.valueToTree(schema)));
            }
            if (tool.parameters().required() != null) {
                tool.parameters().required().forEach(required::add);
            }
        }
        return params.toString();
    }

    public static String transcript(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage) {
                continue; // sent via --system-prompt
            }
            if (message instanceof UserMessage userMessage) {
                sb.append("[USER]\n").append(userMessage.singleText()).append("\n\n");
            } else if (message instanceof AiMessage aiMessage) {
                if (aiMessage.text() != null && !aiMessage.text().isBlank()) {
                    sb.append("[ASSISTANT]\n").append(aiMessage.text()).append("\n\n");
                }
                if (aiMessage.hasToolExecutionRequests()) {
                    sb.append("[ASSISTANT_TOOL_CALLS]\n")
                            .append(toolCallsJson(aiMessage.toolExecutionRequests()))
                            .append("\n\n");
                }
            } else if (message instanceof ToolExecutionResultMessage result) {
                sb.append("[TOOL_RESULT id=").append(result.id())
                        .append(" name=").append(result.toolName()).append("]\n")
                        .append(result.text()).append("\n\n");
            }
        }
        sb.append("Respond now with the structured output.");
        return sb.toString();
    }

    private static String toolCallsJson(List<ToolExecutionRequest> requests) {
        ArrayNode array = MAPPER.createArrayNode();
        for (ToolExecutionRequest request : requests) {
            ObjectNode call = array.addObject();
            call.put("id", request.id());
            call.put("name", request.name());
            call.set("arguments", parseArguments(request.arguments()));
        }
        return array.toString();
    }

    private static JsonNode parseArguments(String arguments) {
        try {
            return MAPPER.readTree(arguments == null ? "{}" : arguments);
        } catch (Exception e) {
            return MAPPER.getNodeFactory().textNode(arguments);
        }
    }
}
