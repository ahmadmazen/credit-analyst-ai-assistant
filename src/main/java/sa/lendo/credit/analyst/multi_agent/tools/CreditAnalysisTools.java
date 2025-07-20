package sa.lendo.credit.analyst.multi_agent.tools;

import dev.langchain4j.agent.tool.Tool;
import sa.lendo.credit.analyst.multi_agent.mock.MockServices;

public class CreditAnalysisTools {
    @Tool("Gets basic customer data like name and age for a given applicant ID")
    public String getCustomerData(String applicantId) {
        return MockServices.getCustomerData(applicantId);
    }

    @Tool("Gets the official credit score for a given applicant ID")
    public String getCreditScore(String applicantId) {
        return MockServices.getCreditScore(applicantId);
    }

    @Tool("Gets the applicant's financial details like income and debt")
    public String getFinancials(String applicantId) {
        return MockServices.getFinancials(applicantId);
    }

    @Tool("Calculates the Debt-to-Income ratio given annual income and total debt")
    public double calculateDtiRatio(double annualIncome, double totalDebt) {
        if (annualIncome == 0) return Double.MAX_VALUE;
        return (totalDebt / annualIncome) * 100;
    }

    @Tool("Gets loan application details like the requested loan amount and the assessed value of the vehicle.")
    public String getApplicationDetails(String applicantId) {
        return MockServices.getApplicationDetails(applicantId);
    }
}
