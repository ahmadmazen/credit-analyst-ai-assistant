package sa.lendo.credit.analyst.multi_agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import sa.lendo.credit.analyst.multi_agent.agents.CreditAnalystAgent;
import sa.lendo.credit.analyst.multi_agent.events.AnalysisEventPublisher;
import sa.lendo.credit.analyst.multi_agent.events.AnalysisRun;
import sa.lendo.credit.analyst.multi_agent.events.AnalysisRunRegistry;
import sa.lendo.credit.analyst.multi_agent.mock.ApplicantProfile;
import sa.lendo.credit.analyst.multi_agent.mock.ApplicantProfileRepository;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public class AnalysisService {

    public static final List<String> SUPPORTED_PRODUCTS = List.of("Car Loan", "Personal Loan");

    private final AgentFactory agentFactory;
    private final ApplicantProfileRepository repository;
    private final AnalysisRunRegistry registry;
    private final Executor executor;
    private final int maxConcurrentRuns;
    private final AtomicInteger activeRuns = new AtomicInteger();

    public AnalysisService(AgentFactory agentFactory,
                           ApplicantProfileRepository repository,
                           AnalysisRunRegistry registry,
                           Executor executor,
                           int maxConcurrentRuns) {
        this.agentFactory = agentFactory;
        this.repository = repository;
        this.registry = registry;
        this.executor = executor;
        this.maxConcurrentRuns = maxConcurrentRuns;
    }

    public String startAnalysis(String applicantId, String productType) {
        ApplicantProfile applicant = repository.findById(applicantId)
                .orElseThrow(() -> new UnknownApplicantException(applicantId));
        if (!SUPPORTED_PRODUCTS.contains(productType)) {
            throw new InvalidProductException(productType);
        }
        if (activeRuns.incrementAndGet() > maxConcurrentRuns) {
            activeRuns.decrementAndGet();
            throw new TooManyRunsException(maxConcurrentRuns);
        }

        String runId = UUID.randomUUID().toString();
        AnalysisEventPublisher publisher = new AnalysisEventPublisher();
        AnalysisRun run = new AnalysisRun(runId, applicantId, productType, publisher);
        registry.register(run);
        publisher.runStarted(runId, applicantId, applicant.name(), productType);

        executor.execute(() -> {
            try {
                analyze(run);
            } finally {
                activeRuns.decrementAndGet();
            }
        });
        return runId;
    }

    private void analyze(AnalysisRun run) {
        AnalysisEventPublisher publisher = run.publisher();
        try {
            CreditAnalystAgent agent = agentFactory.createAgent(publisher);
            String result = agent.analyze(run.applicantId(), run.productType(), loadPolicyRules());
            JsonNode decision = DecisionParser.parse(result);
            run.decision(decision);
            run.status(AnalysisRun.Status.COMPLETED);
            publisher.decision(decision);
            publisher.runCompleted("COMPLETED", System.currentTimeMillis() - run.startedAt());
        } catch (Exception e) {
            run.status(AnalysisRun.Status.FAILED);
            publisher.error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                    "analysis");
            publisher.runCompleted("FAILED", System.currentTimeMillis() - run.startedAt());
        }
    }

    private String loadPolicyRules() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("policy_rules.txt")) {
            if (in == null) {
                throw new IllegalStateException("policy_rules.txt not found on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read policy_rules.txt", e);
        }
    }
}
