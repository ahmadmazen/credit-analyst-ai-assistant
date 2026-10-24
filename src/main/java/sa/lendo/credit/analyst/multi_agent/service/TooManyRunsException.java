package sa.lendo.credit.analyst.multi_agent.service;

public class TooManyRunsException extends RuntimeException {

    public TooManyRunsException(int maxConcurrentRuns) {
        super("Too many analyses in progress (limit " + maxConcurrentRuns + "). Try again shortly.");
    }
}
