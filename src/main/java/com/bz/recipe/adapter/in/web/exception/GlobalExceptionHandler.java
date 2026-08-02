package com.bz.recipe.adapter.in.web.exception;

import com.bz.recipe.adapter.out.persistence.exception.InvalidSortPropertyException;
import com.bz.recipe.domain.exception.DuplicateIngredientException;
import com.bz.recipe.domain.exception.RecipeNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps domain and validation exceptions to RFC 7807 problem-detail responses;
 * validation problems carry a per-field {@code errors} map.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RecipeNotFoundException.class)
    ProblemDetail handleNotFound(
        RecipeNotFoundException ex
    ) {
        var problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Recipe not found");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(
        MethodArgumentNotValidException ex
    ) {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setDetail("Request body validation failed");
        var errors = ex.getBindingResult().getFieldErrors().stream().collect(
            Collectors.toMap(
                FieldError::getField, fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(), (
                    a,
                    b) -> a + "; " + b));
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleParameterValidation(
        HandlerMethodValidationException ex
    ) {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setDetail("Request validation failed");
        var errors = ex.getParameterValidationResults().stream().flatMap(
            result -> result.getResolvableErrors().stream().map(
                error -> Map.entry(
                    error instanceof FieldError fieldError ? fieldError.getField() : String.valueOf(
                        result.getMethodParameter().getParameterName()), error
                            .getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()))).collect(
                                Collectors.toMap(
                                    Map.Entry::getKey, Map.Entry::getValue, (
                                        a,
                                        b) -> a + "; " + b));
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(
        ConstraintViolationException ex
    ) {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setDetail("Request validation failed");
        var errors = ex.getConstraintViolations().stream().collect(
            Collectors.toMap(
                violation -> String.valueOf(violation.getPropertyPath()), violation -> violation
                    .getMessage() == null ? "invalid" : violation.getMessage(), (
                        a,
                        b) -> a + "; " + b));
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(DuplicateIngredientException.class)
    ProblemDetail handleDuplicateIngredient(
        DuplicateIngredientException ex
    ) {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Duplicate ingredient");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /**
     * Malformed JSON or an invalid field value the message converter cannot
     * bind (e.g. an unknown {@code unit} enum constant); without this handler
     * the catch-all would turn a client mistake into a 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(
        HttpMessageNotReadableException ex
    ) {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Malformed request body");
        problem.setDetail("Request body is not readable or contains an invalid value");
        return problem;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail handleMissingHeader(
        MissingRequestHeaderException ex
    ) {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Missing required header");
        problem.setDetail("Required header '" + ex.getHeaderName() + "' is missing");
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLockConflict(
        OptimisticLockingFailureException ex
    ) {
        var problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Concurrent modification");
        problem.setDetail(
            "The recipe was modified concurrently; please retry with the latest state");
        return problem;
    }

    @ExceptionHandler(InvalidSortPropertyException.class)
    ProblemDetail handleUnknownSortProperty(
        InvalidSortPropertyException ex
    ) {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid sort property");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResource(
        NoResourceFoundException ex
    ) {
        var problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Not found");
        problem.setDetail("No resource at '" + ex.getResourcePath() + "'");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(
        Exception ex
    ) {
        log.error("Unexpected error", ex);
        var problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Internal server error");
        problem.setDetail("An unexpected error occurred");
        return problem;
    }
}
