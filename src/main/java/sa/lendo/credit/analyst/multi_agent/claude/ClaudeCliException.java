package sa.lendo.credit.analyst.multi_agent.claude;

public class ClaudeCliException extends RuntimeException {

    public ClaudeCliException(String message) {
        super(message);
    }

    public ClaudeCliException(String message, Throwable cause) {
        super(message, cause);
    }
}
