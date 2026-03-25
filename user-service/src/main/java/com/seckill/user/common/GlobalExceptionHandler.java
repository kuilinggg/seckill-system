package com.seckill.user.common;

import java.sql.SQLIntegrityConstraintViolationException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({DuplicateKeyException.class, DataIntegrityViolationException.class, SQLIntegrityConstraintViolationException.class})
    public Result<Object> handleDuplicateException(Exception ex) {
        Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(ex);
        if (rootCause instanceof SQLIntegrityConstraintViolationException) {
            return Result.error("用户名或手机号已存在，请直接登录");
        }
        if (ex instanceof DuplicateKeyException || ex instanceof DataIntegrityViolationException) {
            return Result.error("用户名或手机号已存在，请直接登录");
        }
        return Result.error("服务器内部错误");
    }

    @ExceptionHandler(Exception.class)
    public Result<Object> handleException(Exception ex) {
        return Result.error("服务器内部错误");
    }
}
