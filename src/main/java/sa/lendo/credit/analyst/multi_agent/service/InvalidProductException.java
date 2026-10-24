package sa.lendo.credit.analyst.multi_agent.service;

public class InvalidProductException extends RuntimeException {

    public InvalidProductException(String productType) {
        super("Unsupported product type '" + productType + "'. Supported: "
                + AnalysisService.SUPPORTED_PRODUCTS);
    }
}
