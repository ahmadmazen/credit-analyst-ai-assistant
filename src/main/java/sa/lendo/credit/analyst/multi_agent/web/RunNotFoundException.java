package sa.lendo.credit.analyst.multi_agent.web;

public class RunNotFoundException extends RuntimeException {

    public RunNotFoundException(String runId) {
        super("No analysis run found with ID '" + runId + "'");
    }
}
