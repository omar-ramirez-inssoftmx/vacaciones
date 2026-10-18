package com.inssoft.people.common.exception;

import com.inssoft.people.common.api.ErrorCode;

public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
