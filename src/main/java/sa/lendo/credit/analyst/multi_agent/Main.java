package sa.lendo.credit.analyst.multi_agent;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import sa.lendo.credit.analyst.multi_agent.agents.CreditAnalystAgent;
import sa.lendo.credit.analyst.multi_agent.tools.CreditAnalysisTools;
import sa.lendo.credit.analyst.multi_agent.tools.ProductGuidelineTools;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;


public class Main {
 final static String OPEN_API_KEY = "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";

    public static void main(String[] args) throws Exception {

                // 1. Setup the LLM
                OpenAiChatModel model = OpenAiChatModel.builder()
                        .apiKey(OPEN_API_KEY)
                        .modelName("gpt-4o")
                        .temperature(0.0) //0.0 (which tells the AI to be deterministic and not get creative).
                        .build();

        // 2. Load the general company rules from the resources folder
        InputStream rulesInputStream = Main.class.getClassLoader().getResourceAsStream("policy_rules.txt");
        if (rulesInputStream == null) {
            throw new RuntimeException("Error: Cannot find 'policy_rules.txt' in src/main/resources. Please ensure the file exists.");
        }
        String rules = new String(rulesInputStream.readAllBytes(), StandardCharsets.UTF_8);

        // 3. Build the primary agent and provide it with all its tools
        CreditAnalystAgent agent = AiServices.builder(CreditAnalystAgent.class)
                .chatLanguageModel(model)
                .tools(new CreditAnalysisTools(), new ProductGuidelineTools(model))
                .build();

        // --- SCENARIO 1: PERSONAL LOAN (tests specific RAG rule) ---
        String applicantId = "APP-123";


//        String personalLoanProductType = "Personal Loan";
//        System.out.printf("--- Starting Analysis for Applicant: %s, Product: %s ---\n", applicantId, personalLoanProductType);
//        String personalLoanResult = agent.analyze(applicantId, personalLoanProductType, rules);
//
//        System.out.println("\n--- LLM Final Analysis (Personal Loan) ---");
//        System.out.println(personalLoanResult);
//        System.out.println("\n============================================\n");


        // --- SCENARIO 2: AUTO LOAN (tests LTV calculation and RAG) ---
        String autoLoanProductType = "Auto Loan";
        System.out.printf("--- Starting Analysis for Applicant: %s, Product: %s ---\n", applicantId, autoLoanProductType);
        String autoLoanResult = agent.analyze(applicantId, autoLoanProductType, rules);

        System.out.println("\n--- LLM Final Analysis (Auto Loan) ---");
        System.out.println(autoLoanResult);

        // Step 5 would be to parse `analysisResult` and populate the template.
    }



    }
