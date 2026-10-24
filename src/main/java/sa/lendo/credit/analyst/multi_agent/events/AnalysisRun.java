package sa.lendo.credit.analyst.multi_agent.events;

import com.fasterxml.jackson.databind.JsonNode;

public class AnalysisRun {

    public enum Status {RUNNING, COMPLETED, FAILED}

    private final String id;
    private final String applicantId;
    private final String productType;
    private final AnalysisEventPublisher publisher;
    private final long startedAt = System.currentTimeMillis();
    private volatile Status status = Status.RUNNING;
    private volatile JsonNode decision;

    public AnalysisRun(String id, String applicantId, String productType, AnalysisEventPublisher publisher) {
        this.id = id;
        this.applicantId = applicantId;
        this.productType = productType;
        this.publisher = publisher;
    }

    public String id() {
        return id;
    }

    public String applicantId() {
        return applicantId;
    }

    public String productType() {
        return productType;
    }

    public AnalysisEventPublisher publisher() {
        return publisher;
    }

    public long startedAt() {
        return startedAt;
    }

    public Status status() {
        return status;
    }

    public void status(Status status) {
        this.status = status;
    }

    public JsonNode decision() {
        return decision;
    }

    public void decision(JsonNode decision) {
        this.decision = decision;
    }
}
