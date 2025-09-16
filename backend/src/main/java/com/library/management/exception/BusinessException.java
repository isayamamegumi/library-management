package com.library.management.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * ビジネスロジック関連の例外
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final Map<String, Object> details;

    public BusinessException(String message) {
        this(message, HttpStatus.BAD_REQUEST, null);
    }

    public BusinessException(String message, HttpStatus httpStatus) {
        this(message, httpStatus, null);
    }

    public BusinessException(String message, HttpStatus httpStatus, Map<String, Object> details) {
        super(message);
        this.httpStatus = httpStatus;
        this.details = details;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    // よく使われるファクトリーメソッド
    public static BusinessException badRequest(String message) {
        return new BusinessException(message, HttpStatus.BAD_REQUEST);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(message, HttpStatus.CONFLICT);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(message, HttpStatus.FORBIDDEN);
    }

    public static BusinessException unprocessableEntity(String message) {
        return new BusinessException(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}