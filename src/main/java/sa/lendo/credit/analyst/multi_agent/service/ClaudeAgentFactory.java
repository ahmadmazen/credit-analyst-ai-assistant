package sa.lendo.credit.analyst.multi_agent.service;

import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;
import sa.lendo.credit.analyst.multi_agent.agents.CreditAnalystAgent;
import sa.lendo.credit.analyst.multi_agent.claude.ClaudeCliProperties;
import sa.lendo.credit.analyst.multi_agent.claude.ClaudeCodeChatModel;
import sa.lendo.credit.analyst.multi_agent.claude.CommandRunner;
import sa.lendo.credit.analyst.multi_agent.events.AnalysisEventPublisher;
import sa.lendo.credit.analyst.multi_agent.mock.ApplicantProfileRepository;
import sa.lendo.credit.analyst.multi_agent.tools.CreditAnalysisTools;
import sa.lendo.credit.analyst.multi_agent.tools.ProductGuidelineTools;

/**
 * Builds a fresh agent graph per analysis run: two Claude-CLI-backed models
 * (credit analyst + nested document analyst), both bound to the run's event
 * publisher so every model turn and tool call streams to the UI.
 */
@Component
public class ClaudeAgentFactory implements AgentFactory {

    private final CommandRunner commandRunner;
    private final ClaudeCliProperties properties;
    private final ApplicantProfileRepository repository;

    public ClaudeAgentFactory(CommandRunner commandRunner,
                              ClaudeCliProperties properties,
                              ApplicantProfileRepository repository) {
        this.commandRunner = commandRunner;
        this.properties = properties;
        this.repository = repository;
    }

    @Override
    public CreditAnalystAgent createAgent(AnalysisEventPublisher publisher) {
        ClaudeCodeChatModel analystModel = ClaudeCodeChatModel.builder()
                .commandRunner(commandRunner)
                .properties(properties)
                .agentName("credit_analyst")
                .finalAnswerSchema(DecisionSchema.NODE)
                .eventPublisher(publisher)
                .build();
        ClaudeCodeChatModel documentAnalystModel = ClaudeCodeChatModel.builder()
                .commandRunner(commandRunner)
                .properties(properties)
                .agentName("document_analyst")
                .eventPublisher(publisher)
                .build();

        return AiServices.builder(CreditAnalystAgent.class)
                .chatLanguageModel(analystModel)
                .tools(new CreditAnalysisTools(repository),
                        new ProductGuidelineTools(documentAnalystModel, publisher))
                .build();
    }
}
