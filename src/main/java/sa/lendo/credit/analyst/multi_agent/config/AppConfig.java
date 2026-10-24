package sa.lendo.credit.analyst.multi_agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sa.lendo.credit.analyst.multi_agent.claude.ClaudeCliLocator;
import sa.lendo.credit.analyst.multi_agent.claude.ClaudeCliProperties;
import sa.lendo.credit.analyst.multi_agent.claude.CommandRunner;
import sa.lendo.credit.analyst.multi_agent.claude.ProcessCommandRunner;
import sa.lendo.credit.analyst.multi_agent.events.AnalysisRunRegistry;
import sa.lendo.credit.analyst.multi_agent.mock.ApplicantProfileRepository;
import sa.lendo.credit.analyst.multi_agent.service.AgentFactory;
import sa.lendo.credit.analyst.multi_agent.service.AnalysisService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties(ClaudeCliProperties.class)
public class AppConfig {

    @Bean
    public CommandRunner commandRunner(ClaudeCliProperties properties) {
        // IDE-launched JVMs often miss ~/.local/bin on PATH; resolve "claude" up front
        properties.setPath(ClaudeCliLocator.resolve(
                properties.getPath(), ClaudeCliLocator.defaultSearchDirs()));
        return new ProcessCommandRunner();
    }

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService analysisExecutor(
            @Value("${analysis.max-concurrent-runs:2}") int maxConcurrentRuns) {
        return Executors.newFixedThreadPool(maxConcurrentRuns);
    }

    @Bean
    public AnalysisService analysisService(AgentFactory agentFactory,
                                           ApplicantProfileRepository repository,
                                           AnalysisRunRegistry registry,
                                           ExecutorService analysisExecutor,
                                           @Value("${analysis.max-concurrent-runs:2}") int maxConcurrentRuns) {
        return new AnalysisService(agentFactory, repository, registry, analysisExecutor, maxConcurrentRuns);
    }
}
