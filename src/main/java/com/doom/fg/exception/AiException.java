package com.doom.fg.exception;

import lombok.Getter;

@Getter
public class AiException extends RuntimeException {
    private final String errorCode;
    private final String userMessage;

    public AiException(String errorCode, String userMessage) {
        super(userMessage);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }
}
