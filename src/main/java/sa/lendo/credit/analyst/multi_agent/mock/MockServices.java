package sa.lendo.credit.analyst.multi_agent.mock;

public class MockServices {
    public static String getCustomerData(String applicantId) {
        return "{ \"name\": \"John Doe\", \"age\": 28 }";
    }
    public static String getCreditScore(String applicantId) {
        return "{ \"score\": 690 }";
    }
    public static String getFinancials(String applicantId) {
        return "{ \"annualIncome\": 85000, \"totalExistingDebt\": 25000 }";
    }
    public static String getApplicationDetails(String applicantId) {
        // We are using values that will FAIL the LTV check (27k/29k is ~93.1% > 90%)
        // This makes the test more interesting.
        return "{ \"requestedLoanAmount\": 27000, \"vehicleAssessedValue\": 29000 }";
    }
}