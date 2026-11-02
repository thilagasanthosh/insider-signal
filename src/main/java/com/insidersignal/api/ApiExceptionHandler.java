package com.insidersignal.api;

import com.insidersignal.ingest.EventStreamUnavailableException;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Returns every error as an RFC 7807 problem, with field-level messages where they exist. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    var errors = new TreeMap<String, String>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
    return handleExceptionInternal(ex, invalid(errors), headers, status, request);
  }

  @ExceptionHandler(InvalidRuleConfigException.class)
  ProblemDetail handleInvalidRuleConfig(InvalidRuleConfigException ex) {
    return invalid(ex.errors());
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
    var errors = new TreeMap<String, String>();
    ex.getConstraintViolations()
        .forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
    return invalid(errors);
  }

  @ExceptionHandler(EventStreamUnavailableException.class)
  ProblemDetail handleStreamUnavailable(EventStreamUnavailableException ex) {
    log.error("Event stream unavailable", ex);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE, "Events could not be accepted; retry the batch.");
  }

  private static ProblemDetail invalid(Map<String, String> errors) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed.");
    problem.setProperty("errors", errors);
    return problem;
  }
}
