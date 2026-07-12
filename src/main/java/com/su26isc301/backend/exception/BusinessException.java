package com.su26isc301.backend.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
}
