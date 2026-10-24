package sa.lendo.credit.analyst.multi_agent.claude;

import java.time.Duration;
import java.util.List;

public interface CommandRunner {

    CommandResult run(List<String> command, String stdin, Duration timeout);
}
