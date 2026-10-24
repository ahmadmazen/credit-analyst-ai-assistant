package sa.lendo.credit.analyst.multi_agent.claude;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a bare CLI name (e.g. "claude") to an absolute path. IDE-launched JVMs
 * often get a minimal PATH without ~/.local/bin, so relying on PATH alone makes
 * ProcessBuilder fail with "No such file or directory".
 */
public final class ClaudeCliLocator {

    private ClaudeCliLocator() {
    }

    public static String resolve(String configured, List<Path> searchDirs) {
        if (configured == null || configured.contains(File.separator)) {
            return configured; // explicit path: trust it as-is
        }
        for (Path dir : searchDirs) {
            Path candidate = dir.resolve(configured);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
        }
        return configured; // let ProcessBuilder try PATH; its error message names the command
    }

    public static List<Path> defaultSearchDirs() {
        List<Path> dirs = new ArrayList<>();
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String entry : pathEnv.split(File.pathSeparator)) {
                if (!entry.isBlank()) {
                    dirs.add(Path.of(entry));
                }
            }
        }
        String home = System.getProperty("user.home");
        dirs.add(Path.of(home, ".local", "bin"));
        dirs.add(Path.of(home, ".claude", "local"));
        dirs.add(Path.of(home, "bin"));
        dirs.add(Path.of("/opt/homebrew/bin"));
        dirs.add(Path.of("/usr/local/bin"));
        return dirs;
    }
}
