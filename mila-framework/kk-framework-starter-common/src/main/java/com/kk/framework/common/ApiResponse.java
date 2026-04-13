package com.kk.framework.common;

import lombok.Data;
import java.io.Serializable;

@Data
public class ApiResponse<T> implements Serializable {
    private int code;
    private String message;
    private T data;
    private long timestamp;
    private String traceId;
    private String path;

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ok("success", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.setCode(0);
        resp.setMessage(message);
        resp.setData(data);
        resp.setTimestamp(System.currentTimeMillis());
        return resp;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.setCode(code);
        resp.setMessage(message);
        resp.setTimestamp(System.currentTimeMillis());
        return resp;
    }

    public static <T> ApiResponse<T> error(IBizCode bizCode) {
        return error(bizCode.getCode(), bizCode.getMessage());
    }

    public ApiResponse<T> traceId(String traceId) {
        this.setTraceId(traceId);
        return this;
    }

    public ApiResponse<T> path(String path) {
        this.setPath(path);
        return this;
    }
}
