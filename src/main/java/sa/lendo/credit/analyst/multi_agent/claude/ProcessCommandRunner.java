package sa.lendo.credit.analyst.multi_agent.claude;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ProcessCommandRunner implements CommandRunner {

    @Override
    public CommandResult run(List<String> command, String stdin, Duration timeout) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).start();
            Process p = process;
            // drain both streams on background threads to avoid pipe-buffer deadlock
            CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readAll(p, false));
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readAll(p, true));

            try (OutputStream in = process.getOutputStream()) {
                if (stdin != null) {
                    in.write(stdin.getBytes(StandardCharsets.UTF_8));
                }
            }

            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new ClaudeCliException(
                        "Command timed out after " + timeout.toSeconds() + "s: " + command.get(0));
            }
            return new CommandResult(process.exitValue(), stdout.join(), stderr.join());
        } catch (IOException e) {
            throw new ClaudeCliException("Failed to run command " + command.get(0) + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new ClaudeCliException("Interrupted while waiting for " + command.get(0), e);
        }
    }

    private static String readAll(Process process, boolean errorStream) {
        try (var stream = errorStream ? process.getErrorStream() : process.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
