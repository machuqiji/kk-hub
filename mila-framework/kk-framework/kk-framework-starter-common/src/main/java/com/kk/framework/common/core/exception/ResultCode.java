package com.kk.framework.common.core.exception;

public enum ResultCode implements IBizCode {
    SUCCESS(0, "success"),
    VALIDATION_ERROR(40001, "参数校验失败"),
    UNAUTHORIZED(40101, "未登录"),
    FORBIDDEN(40301, "无权限"),
    NOT_FOUND(40401, "资源不存在"),
    INTERNAL_ERROR(50001, "系统异常");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() { return code; }
    @Override
    public String getMessage() { return message; }
}
