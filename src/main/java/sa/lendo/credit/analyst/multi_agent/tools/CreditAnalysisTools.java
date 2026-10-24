package sa.lendo.credit.analyst.multi_agent.tools;

import dev.langchain4j.agent.tool.Tool;
import sa.lendo.credit.analyst.multi_agent.mock.ApplicantProfile;
import sa.lendo.credit.analyst.multi_agent.mock.ApplicantProfileRepository;

import java.util.function.Function;

public class CreditAnalysisTools {

    private final ApplicantProfileRepository repository;

    public CreditAnalysisTools(ApplicantProfileRepository repository) {
        this.repository = repository;
    }

    @Tool("Gets basic customer data like name and age for a given applicant ID")
    public String getCustomerData(String applicantId) {
        return lookup(applicantId, p ->
                "{ \"name\": \"" + p.name() + "\", \"age\": " + p.age() + " }");
    }

    @Tool("Gets the official credit score for a given applicant ID")
    public String getCreditScore(String applicantId) {
        return lookup(applicantId, p -> "{ \"score\": " + p.creditScore() + " }");
    }

    @Tool("Gets the applicant's financial details like income and debt")
    public String getFinancials(String applicantId) {
        return lookup(applicantId, p ->
                "{ \"annualIncome\": " + (long) p.annualIncome()
                        + ", \"totalExistingDebt\": " + (long) p.totalExistingDebt() + " }");
    }

    @Tool("Calculates the Debt-to-Income ratio given annual income and total debt")
    public double calculateDtiRatio(double annualIncome, double totalDebt) {
        if (annualIncome == 0) return Double.MAX_VALUE;
        return (totalDebt / annualIncome) * 100;
    }

    @Tool("Gets loan application details like the requested loan amount and the assessed value of the vehicle.")
    public String getApplicationDetails(String applicantId) {
        return lookup(applicantId, p ->
                "{ \"requestedLoanAmount\": " + (long) p.requestedLoanAmount()
                        + ", \"vehicleAssessedValue\": " + (long) p.vehicleAssessedValue() + " }");
    }

    private String lookup(String applicantId, Function<ApplicantProfile, String> toJson) {
        return repository.findById(applicantId)
                .map(toJson)
                .orElse("{ \"error\": \"No applicant found with ID '" + applicantId + "'\" }");
    }
}
