package sa.lendo.credit.analyst.multi_agent.service;

public class UnknownApplicantException extends RuntimeException {

    public UnknownApplicantException(String applicantId) {
        super("No applicant found with ID '" + applicantId + "'");
    }
}
