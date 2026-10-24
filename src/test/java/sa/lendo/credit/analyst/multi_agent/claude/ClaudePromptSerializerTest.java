package sa.lendo.credit.analyst.multi_agent.claude;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import sa.lendo.credit.analyst.multi_agent.tools.CreditAnalysisTools;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudePromptSerializerTest {

    private static List<ToolSpecification> creditTools() {
        return ToolSpecifications.toolSpecificationsFrom(CreditAnalysisTools.class);
    }

    @Test
    void systemPromptContainsAgentInstructionsAndToolCatalog() {
        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are a diligent credit analyst."),
                UserMessage.from("Analyze APP-123"));

        String prompt = ClaudePromptSerializer.systemPrompt(messages, creditTools());

        assertThat(prompt).contains("You are a diligent credit analyst.");
        // the emulation preamble and the tool catalog
        assertThat(prompt).contains("tool_calls").contains("final_answer");
        assertThat(prompt).contains("getCreditScore")
                .contains("calculateDtiRatio")
                .contains("annualIncome")
                .contains("totalDebt");
    }

    @Test
    void systemPromptWithoutToolsHasNoToolCatalog() {
        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are an expert document analyst."),
                UserMessage.from("Analyze this document"));

        String prompt = ClaudePromptSerializer.systemPrompt(messages, List.of());

        assertThat(prompt).contains("You are an expert document analyst.");
        assertThat(prompt).doesNotContain("getCreditScore");
    }

    @Test
    void transcriptRendersConversationBlocksInOrder() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1").name("getCreditScore")
                .arguments("{\"applicantId\":\"APP-123\"}")
                .build();
        List<ChatMessage> messages = List.of(
                SystemMessage.from("system text stays out of the transcript"),
                UserMessage.from("Please analyze applicant APP-123"),
                AiMessage.from(request),
                ToolExecutionResultMessage.from(request, "{ \"score\": 690 }"));

        String transcript = ClaudePromptSerializer.transcript(messages);

        assertThat(transcript).doesNotContain("system text stays out of the transcript");
        assertThat(transcript).contains("[USER]").contains("Please analyze applicant APP-123");
        assertThat(transcript).contains("[ASSISTANT_TOOL_CALLS]")
                .contains("getCreditScore").contains("APP-123");
        assertThat(transcript).contains("[TOOL_RESULT id=call-1 name=getCreditScore]")
                .contains("690");
        int userIdx = transcript.indexOf("[USER]");
        int callsIdx = transcript.indexOf("[ASSISTANT_TOOL_CALLS]");
        int resultIdx = transcript.indexOf("[TOOL_RESULT");
        assertThat(userIdx).isLessThan(callsIdx);
        assertThat(callsIdx).isLessThan(resultIdx);
        assertThat(transcript.trim()).endsWith("Respond now with the structured output.");
    }

    @Test
    void transcriptRendersPlainAssistantText() {
        List<ChatMessage> messages = List.of(
                UserMessage.from("hello"),
                AiMessage.from("previous assistant answer"));

        String transcript = ClaudePromptSerializer.transcript(messages);

        assertThat(transcript).contains("[ASSISTANT]").contains("previous assistant answer");
    }
}
