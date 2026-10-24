package sa.lendo.credit.analyst.multi_agent.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import sa.lendo.credit.analyst.multi_agent.mock.ApplicantProfileRepository;
import sa.lendo.credit.analyst.multi_agent.service.AnalysisService;

import java.util.Map;

@RestController
public class ScenarioController {

    private final ApplicantProfileRepository repository;

    public ScenarioController(ApplicantProfileRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/scenarios")
    public Map<String, Object> scenarios() {
        return Map.of(
                "applicants", repository.findAll(),
                "products", AnalysisService.SUPPORTED_PRODUCTS);
    }
}
