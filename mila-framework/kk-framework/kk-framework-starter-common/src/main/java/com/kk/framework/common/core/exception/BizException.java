package com.kk.framework.common.core.exception;

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

    public static BizException of(IBizCode bizCode) {
        return new BizException(bizCode.getCode(), bizCode.getMessage());
    }

    public static BizException of(IBizCode bizCode, String message) {
        return new BizException(bizCode.getCode(), message);
    }

    public static BizException of(int code, String message) {
        return new BizException(code, message);
    }
}
