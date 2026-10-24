package sa.lendo.credit.analyst.multi_agent.mock;

public record ApplicantProfile(
        String id,
        String name,
        int age,
        int creditScore,
        double annualIncome,
        double totalExistingDebt,
        double requestedLoanAmount,
        double vehicleAssessedValue,
        String description) {
}
