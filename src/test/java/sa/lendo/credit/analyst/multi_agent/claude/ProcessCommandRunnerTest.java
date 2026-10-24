package sa.lendo.credit.analyst.multi_agent.claude;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessCommandRunnerTest {

    private final ProcessCommandRunner runner = new ProcessCommandRunner();

    @Test
    void capturesStdoutStderrAndExitCode() {
        CommandResult result = runner.run(
                List.of("/bin/sh", "-c", "cat; echo oops >&2"),
                "hello from stdin",
                Duration.ofSeconds(10));

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("hello from stdin");
        assertThat(result.stderr().trim()).isEqualTo("oops");
    }

    @Test
    void returnsNonZeroExitCode() {
        CommandResult result = runner.run(
                List.of("/bin/sh", "-c", "exit 3"), null, Duration.ofSeconds(10));

        assertThat(result.exitCode()).isEqualTo(3);
    }

    @Test
    @Timeout(5)
    void killsProcessOnTimeout() {
        assertThatThrownBy(() -> runner.run(
                List.of("/bin/sleep", "30"), null, Duration.ofMillis(300)))
                .isInstanceOf(ClaudeCliException.class)
                .hasMessageContaining("timed out");
    }
}
