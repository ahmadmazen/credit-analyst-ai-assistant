package sa.lendo.credit.analyst.multi_agent.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;

/**
 * Builds the JSON schema passed to `claude -p --json-schema`, forcing the model to
 * answer with either tool-call requests or the final answer — the function-calling
 * emulation contract that ClaudeResponseParser reads back.
 */
public final class ClaudeSchemaBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ClaudeSchemaBuilder() {
    }

    public static ObjectNode wrapperSchema(List<ToolSpecification> tools, JsonNode finalAnswerSchema) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");

        ObjectNode action = props.putObject("action");
        action.put("type", "string");
        action.putArray("enum").add("tool_calls").add("final_answer");

        ObjectNode toolCalls = props.putObject("tool_calls");
        toolCalls.put("type", "array");
        ObjectNode items = toolCalls.putObject("items");
        items.put("type", "object");
        ObjectNode itemProps = items.putObject("properties");
        ObjectNode name = itemProps.putObject("name");
        name.put("type", "string");
        // restricting names to the registered tools prevents hallucinated tool names,
        // which would NPE inside langchain4j's tool-executor lookup
        ArrayNode nameEnum = name.putArray("enum");
        tools.forEach(t -> nameEnum.add(t.name()));
        ObjectNode arguments = itemProps.putObject("arguments");
        arguments.put("type", "object");
        arguments.put("additionalProperties", true);
        items.putArray("required").add("name").add("arguments");

        props.set("final_answer", finalAnswerSchema != null
                ? finalAnswerSchema.deepCopy()
                : MAPPER.createObjectNode().put("type", "string"));

        root.putArray("required").add("action");
        return root;
    }
}
