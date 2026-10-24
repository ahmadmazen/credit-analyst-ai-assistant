package sa.lendo.credit.analyst.multi_agent.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import sa.lendo.credit.analyst.multi_agent.events.AnalysisRunRegistry;
import sa.lendo.credit.analyst.multi_agent.mock.ApplicantProfileRepository;
import sa.lendo.credit.analyst.multi_agent.service.AgentFactory;
import sa.lendo.credit.analyst.multi_agent.service.AnalysisService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalysisApiTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ApplicantProfileRepository repository = new ApplicantProfileRepository();
    private final AnalysisRunRegistry registry = new AnalysisRunRegistry();
    private final AgentFactory happyFactory = publisher -> (a, b, c) ->
            "{\"decision\":\"APPROVE\",\"summary\":\"All rules pass\",\"reasoningSteps\":[]}";

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = mvc(new AnalysisService(happyFactory, repository, registry, Runnable::run, 2));
    }

    private MockMvc mvc(AnalysisService service) {
        return MockMvcBuilders
                .standaloneSetup(new AnalysisController(service, registry),
                        new ScenarioController(repository),
                        new RulesController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String startBody(String applicantId, String productType) throws Exception {
        return MAPPER.writeValueAsString(
                new AnalysisController.AnalysisRequest(applicantId, productType));
    }

    @Test
    void scenariosEndpointListsApplicantsAndProducts() throws Exception {
        mvc.perform(get("/api/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicants.length()").value(5))
                .andExpect(jsonPath("$.applicants[0].id").value("APP-001"))
                .andExpect(jsonPath("$.products[0]").value("Car Loan"))
                .andExpect(jsonPath("$.products[1]").value("Personal Loan"));
    }

    @Test
    void rulesEndpointServesPolicyAndProductGuides() throws Exception {
        mvc.perform(get("/api/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy").value(
                        org.hamcrest.Matchers.containsString("credit score must be 680")))
                .andExpect(jsonPath("$.products['Car Loan']").value(
                        org.hamcrest.Matchers.containsString("90% of the vehicle's assessed value")))
                .andExpect(jsonPath("$.products['Personal Loan']").value(
                        org.hamcrest.Matchers.containsString("credit score below 700")));
    }

    @Test
    void startReturns202WithRunId() throws Exception {
        mvc.perform(post("/api/analysis").contentType("application/json")
                        .content(startBody("APP-001", "Car Loan")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").isNotEmpty());
    }

    @Test
    void startReturns404ForUnknownApplicant() throws Exception {
        mvc.perform(post("/api/analysis").contentType("application/json")
                        .content(startBody("APP-999", "Car Loan")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("APP-999")));
    }

    @Test
    void startReturns400ForUnsupportedProduct() throws Exception {
        mvc.perform(post("/api/analysis").contentType("application/json")
                        .content(startBody("APP-001", "Yacht Loan")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startReturns429WhenConcurrencyLimitReached() throws Exception {
        MockMvc limited = mvc(new AnalysisService(happyFactory, repository, registry,
                command -> { /* never runs: stays RUNNING */ }, 1));

        limited.perform(post("/api/analysis").contentType("application/json")
                        .content(startBody("APP-001", "Car Loan")))
                .andExpect(status().isAccepted());
        limited.perform(post("/api/analysis").contentType("application/json")
                        .content(startBody("APP-002", "Car Loan")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void snapshotReturnsRunStateAndDecision() throws Exception {
        MvcResult started = mvc.perform(post("/api/analysis").contentType("application/json")
                        .content(startBody("APP-001", "Car Loan")))
                .andReturn();
        String runId = MAPPER.readTree(started.getResponse().getContentAsString())
                .path("runId").asText();

        mvc.perform(get("/api/analysis/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.decision.decision").value("APPROVE"))
                .andExpect(jsonPath("$.events[0].type").value("run_started"));
    }

    @Test
    void snapshotReturns404ForUnknownRun() throws Exception {
        mvc.perform(get("/api/analysis/nope")).andExpect(status().isNotFound());
    }

    @Test
    void eventsStreamReplaysBufferedEventsForCompletedRun() throws Exception {
        MvcResult started = mvc.perform(post("/api/analysis").contentType("application/json")
                        .content(startBody("APP-002", "Car Loan")))
                .andReturn();
        String runId = MAPPER.readTree(started.getResponse().getContentAsString())
                .path("runId").asText();

        MvcResult result = mvc.perform(get("/api/analysis/" + runId + "/events"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:run_started")
                .contains("event:decision")
                .contains("event:run_completed");
    }

    @Test
    void eventsStreamReturns404ForUnknownRun() throws Exception {
        mvc.perform(get("/api/analysis/nope/events")).andExpect(status().isNotFound());
    }
}
