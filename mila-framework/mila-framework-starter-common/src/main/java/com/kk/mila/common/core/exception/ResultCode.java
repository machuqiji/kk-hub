package com.kk.mila.common.core.exception;

public enum ResultCode implements IBizCode {
    SUCCESS(0, "success"),
    FAIL(-1, "操作失败"),
    ERROR(500, "系统异常"),
    PARAM_INVALID(400, "参数无效"),
    ;

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
