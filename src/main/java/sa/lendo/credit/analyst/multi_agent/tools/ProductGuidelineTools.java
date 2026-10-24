package sa.lendo.credit.analyst.multi_agent.tools;


import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import sa.lendo.credit.analyst.multi_agent.agents.DocumentAnalystAgent;
import sa.lendo.credit.analyst.multi_agent.events.AnalysisEventPublisher;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ProductGuidelineTools {

    private final ChatLanguageModel model;
    private final AnalysisEventPublisher publisher;

    // We pass the model in so our tool can build its own internal agent
    public ProductGuidelineTools(ChatLanguageModel model, AnalysisEventPublisher publisher) {
        this.model = model;
        this.publisher = publisher;
    }

    @Tool("Retrieves and analyzes product-specific guidelines to answer a question.")
    public String getProductSpecificGuideline(String productType, String question) throws Exception {
        String fileName = productType.toLowerCase().replace(" ", "_") + "_guide.txt";
        if (publisher != null) {
            publisher.guidelineLookup(productType, question, fileName);
        }

        // 1. Load the correct document from resources
        InputStream docStream = getClass().getClassLoader().getResourceAsStream(fileName);
        if (docStream == null) {
            return "Error: Could not find a guide document named '" + fileName + "'";
        }
        String documentText = new String(docStream.readAllBytes(), StandardCharsets.UTF_8);

        // 2. Create the specialist Document Analyst Agent on-the-fly
        DocumentAnalystAgent docAgent = AiServices.builder(DocumentAnalystAgent.class)
                .chatLanguageModel(model)
                .build();

        // 3. Ask the specialist agent the question about the document
        return docAgent.analyzeDocument(documentText, question);
    }
}
