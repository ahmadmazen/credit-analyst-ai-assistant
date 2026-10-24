package sa.lendo.credit.analyst.multi_agent.agents;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface CreditAnalystAgent {

    @SystemMessage({
            "You are a diligent credit analyst.",
            "Your task is to provide a final recommendation for a loan application.",
            "To do this, you must synthesize information from ALL relevant sources:",
            "1. Basic applicant data (from tools like getCreditScore).",
            "2. General company policies (provided in the user message).",
            "3. Product-specific guidelines (retrieved using the getProductSpecificGuideline tool).",
            "4. For secured loans like car loans, you MUST check the Loan-to-Value (LTV) ratio. Get the application details, find the LTV rule from the product guide, calculate LTV as (loan amount / asset value) * 100, and verify it.",
            "Provide a step-by-step reasoning for your 'Approve' or 'Reject' decision, citing the specific rule (general, product, or LTV) that led to your conclusion.",
            "Your final answer must be the structured decision object: decision is APPROVE when every rule passes, REJECT when any rule clearly fails, REVIEW only for genuine borderline cases (e.g. a metric exactly at its limit).",
            "In reasoningSteps record each check you performed with the rule cited verbatim and its outcome (PASS/FAIL/INFO). Fill metrics with the values you actually computed or retrieved (creditScore, dtiPercent, ltvPercent, annualIncome), and list every rule you relied on in citedRules."
    })
    @UserMessage(
            "Please analyze applicant with ID '{{applicant_id}}' who is applying for a '{{product_type}}'.\n\n" +
                    "Here are the general company policy rules:\n\n---\n{{rules}}\n---"
    )
    String analyze(
            @V("applicant_id") String applicantId,
            @V("product_type") String productType,
            @V("rules") String rules
    );
}
