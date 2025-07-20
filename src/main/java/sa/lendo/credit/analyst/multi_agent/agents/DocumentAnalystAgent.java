package sa.lendo.credit.analyst.multi_agent.agents;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface DocumentAnalystAgent {

    @SystemMessage({
            "You are an expert document analyst.",
            "You will be given a document and a question.",
            "Your task is to answer the question based ONLY on the information within the provided document.",
            "If the answer cannot be found in the document, you must state that clearly.",
            "Do not use any external knowledge."
    })
    // 1. Add the @UserMessage annotation with a template
    @UserMessage(
            "Please analyze the following document:\n\n" +
                    "--- DOCUMENT START ---\n" +
                    "{{doc_text}}\n" +
                    "--- DOCUMENT END ---\n\n" +
                    "Now, answer this question based only on the document: {{question}}"
    )
        // 2. Add the @V annotation to map parameters to the template
    String analyzeDocument(
            @V("doc_text") String documentText,
            @V("question") String question
    );
}
