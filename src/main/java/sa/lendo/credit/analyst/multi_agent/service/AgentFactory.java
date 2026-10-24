package sa.lendo.credit.analyst.multi_agent.service;

import sa.lendo.credit.analyst.multi_agent.agents.CreditAnalystAgent;
import sa.lendo.credit.analyst.multi_agent.events.AnalysisEventPublisher;

public interface AgentFactory {

    CreditAnalystAgent createAgent(AnalysisEventPublisher publisher);
}
