package com.kk.framework.common.core.exception;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExceptionCode {
    int code();
    String message();
}
