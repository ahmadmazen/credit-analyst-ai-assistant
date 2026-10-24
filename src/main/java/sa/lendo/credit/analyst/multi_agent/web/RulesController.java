package sa.lendo.credit.analyst.multi_agent.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import sa.lendo.credit.analyst.multi_agent.service.AnalysisService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serves the rule documents the agents actually reason over, so the UI can show
 * them side by side with the analysis result.
 */
@RestController
public class RulesController {

    private final Map<String, Object> rules;

    public RulesController() {
        Map<String, String> products = new LinkedHashMap<>();
        for (String product : AnalysisService.SUPPORTED_PRODUCTS) {
            // same filename convention ProductGuidelineTools uses
            products.put(product, readResource(product.toLowerCase().replace(" ", "_") + "_guide.txt"));
        }
        this.rules = Map.of(
                "policy", readResource("policy_rules.txt"),
                "products", products);
    }

    @GetMapping("/api/rules")
    public Map<String, Object> rules() {
        return rules;
    }

    private static String readResource(String name) {
        try (InputStream in = getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                return "(document '" + name + "' not found)";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "(could not read '" + name + "')";
        }
    }

    private static ClassLoader getClassLoader() {
        return RulesController.class.getClassLoader();
    }
}
