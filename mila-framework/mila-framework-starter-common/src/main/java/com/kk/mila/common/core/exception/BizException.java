package com.kk.mila.common.core.exception;

public class BizException extends RuntimeException {
    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(IBizCode bizCode) {
        super(bizCode.getMessage());
        this.code = bizCode.getCode();
    }

    public int getCode() {
        return code;
    }
}
