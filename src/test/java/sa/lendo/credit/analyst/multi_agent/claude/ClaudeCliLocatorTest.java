package sa.lendo.credit.analyst.multi_agent.claude;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeCliLocatorTest {

    @TempDir
    Path tempDir;

    private Path executable(String name) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, "#!/bin/sh\n");
        file.toFile().setExecutable(true);
        return file;
    }

    @Test
    void resolvesBareNameAgainstSearchDirs() throws IOException {
        Path claude = executable("claude");

        String resolved = ClaudeCliLocator.resolve("claude", List.of(tempDir));

        assertThat(resolved).isEqualTo(claude.toAbsolutePath().toString());
    }

    @Test
    void keepsBareNameWhenNotFoundAnywhere() {
        assertThat(ClaudeCliLocator.resolve("claude", List.of(tempDir.resolve("missing"))))
                .isEqualTo("claude");
    }

    @Test
    void leavesExplicitPathsUntouched() {
        assertThat(ClaudeCliLocator.resolve("/opt/somewhere/claude", List.of(tempDir)))
                .isEqualTo("/opt/somewhere/claude");
    }

    @Test
    void skipsNonExecutableFiles() throws IOException {
        Path file = tempDir.resolve("claude");
        Files.writeString(file, "not executable");
        file.toFile().setExecutable(false);

        assertThat(ClaudeCliLocator.resolve("claude", List.of(tempDir))).isEqualTo("claude");
    }

    @Test
    void defaultSearchDirsIncludeCommonInstallLocations() {
        List<Path> dirs = ClaudeCliLocator.defaultSearchDirs();
        String home = System.getProperty("user.home");

        assertThat(dirs).contains(Path.of(home, ".local", "bin"));
        assertThat(dirs.stream().map(Path::toString)).anyMatch(d -> d.equals("/opt/homebrew/bin"));
    }
}
