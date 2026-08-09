package com.lukanizharadze.minibanking.exception;



import com.lukanizharadze.minibanking.dto.TransactionResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;


@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
        return createProblem(
                HttpStatus.NOT_FOUND,
                ErrorCode.ACCOUNT_NOT_FOUND,
                ex.getMessage()
        );
    }



    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        FieldError error = ex.getBindingResult().getFieldError();
        String detail = error.getField() + " " + error.getDefaultMessage();

        return createProblem(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                detail
        );
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ProblemDetail handleInvalidRequest() {
        return createProblem(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_REQUEST,
                "Request body or parameter is invalid"
        );
    }

    @ExceptionHandler(SameAccountTransactionException.class)
    public ProblemDetail handleSameAccountTransaction(SameAccountTransactionException ex) {
        return createProblem(
                HttpStatus.BAD_REQUEST,
                ErrorCode.SAME_ACCOUNT_TRANSACTION,
                ex.getMessage()
        );

    }

    @ExceptionHandler(TransactionRejectedException.class)
    public ProblemDetail handleTransactionRejected(TransactionRejectedException ex) {
        return createProblem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ex.getReason().name(),
                ex.getMessage()
        );


    }


    @ExceptionHandler(AccountNotEmptyException.class)
    public ProblemDetail handleAccountNotEmpty(AccountNotEmptyException ex) {
        return createProblem(
                HttpStatus.CONFLICT,
                ErrorCode.ACCOUNT_NOT_EMPTY,
                ex.getMessage()
        );
    }

    private ProblemDetail createProblem(HttpStatus status, ErrorCode code, String detail) {
        return createProblem(status, code.name(), detail);
    }

    private ProblemDetail createProblem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingIdempotencyKey() {
        return createProblem(
                HttpStatus.BAD_REQUEST,
                ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                "Idempotency-Key header is required"
        );

    }
    @ExceptionHandler(IdempotencyException.class)
    public ProblemDetail handleIdempotencyConflict(IdempotencyException ex) {
        return createProblem(
                HttpStatus.CONFLICT,
                ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                ex.getMessage()
        );
    }



}