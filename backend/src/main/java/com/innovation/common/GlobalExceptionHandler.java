package com.innovation.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<?> handleBadCredentials(BadCredentialsException e) {
        return R.fail(401, "用户名或密码错误");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<?> handleAccessDenied(AccessDeniedException e) {
        return R.fail(403, "无权限访问");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.OK)
    public R<?> handleValidation(Exception e) {
        String msg = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException) {
            var error = ((MethodArgumentNotValidException) e).getBindingResult().getFieldError();
            if (error != null) msg = error.getDefaultMessage();
        }
        return R.fail(400, msg);
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<?> handleBusiness(BusinessException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<?> handleException(Exception e) {
        log.error("系统异常", e);
        return R.fail("系统内部错误");
    }
}
