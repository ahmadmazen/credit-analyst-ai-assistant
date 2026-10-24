package sa.lendo.credit.analyst.multi_agent.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sa.lendo.credit.analyst.multi_agent.claude.ClaudeCliException;
import sa.lendo.credit.analyst.multi_agent.service.InvalidProductException;
import sa.lendo.credit.analyst.multi_agent.service.TooManyRunsException;
import sa.lendo.credit.analyst.multi_agent.service.UnknownApplicantException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({UnknownApplicantException.class, RunNotFoundException.class})
    public ProblemDetail notFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InvalidProductException.class)
    public ProblemDetail badRequest(InvalidProductException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(TooManyRunsException.class)
    public ProblemDetail tooManyRequests(TooManyRunsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(ClaudeCliException.class)
    public ProblemDetail claudeFailure(ClaudeCliException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "The Claude CLI call failed: " + e.getMessage());
    }
}
