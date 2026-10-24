package sa.lendo.credit.analyst.multi_agent.claude;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manual smoke test against the real `claude` CLI. Requires an authenticated
 * Claude Code installation; run it once after changing the CLI invocation:
 *   ./mvnw test -Dtest=ClaudeCodeChatModelIT -Dsurefire.failIfNoSpecifiedTests=false
 * (remove @Disabled locally to run)
 */
@Disabled("manual: requires an authenticated claude CLI; slow")
class ClaudeCodeChatModelIT {

    @Test
    void plainTextRoundTrip() {
        ClaudeCliProperties props = new ClaudeCliProperties();
        props.setTimeoutSeconds(120);
        ClaudeCodeChatModel model = ClaudeCodeChatModel.builder()
                .commandRunner(new ProcessCommandRunner())
                .properties(props)
                .agentName("smoke_test")
                .build();

        Response<AiMessage> response = model.generate(List.of(
                SystemMessage.from("Answer with a single word."),
                UserMessage.from("Say OK")));

        assertThat(response.content().text()).containsIgnoringCase("ok");
    }
}
