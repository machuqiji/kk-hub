package com.kk.mila.common.core.exception;

public enum ExceptionCode implements IBizCode {
    // 通用错误码
    PARAM_INVALID(1001, "参数无效"),
    DATA_NOT_FOUND(1002, "数据不存在"),
    DATA_DUPLICATE(1003, "数据重复"),
    ;

    private final int code;
    private final String message;

    ExceptionCode(int code, String message) {
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
