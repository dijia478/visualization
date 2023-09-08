package com.dijia478.visualization.advice;

import com.dijia478.visualization.bean.BaseResponse;
import com.dijia478.visualization.bean.LoanException;
import com.dijia478.visualization.bean.ResultEnum;
import com.dijia478.visualization.util.I18nUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常统一处理类
 *
 * @author dijia478
 * @date 2023-8-9 16:56:36
 */
@RestControllerAdvice
@Slf4j
public class ExceptionControllerAdvice {

    @Autowired
    private I18nUtil i18nUtil;

    /**
     * Controller层之前的常见相关异常
     *
     * @param e 异常
     * @return 返回结果
     */
    @ExceptionHandler({
            NoHandlerFoundException.class,
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class,
            MethodArgumentTypeMismatchException.class,
            MissingPathVariableException.class,
            MissingServletRequestParameterException.class,
            TypeMismatchException.class,
            HttpMessageNotReadableException.class,
            HttpMessageNotWritableException.class,
            HttpMediaTypeNotAcceptableException.class,
            ServletRequestBindingException.class,
            ConversionNotSupportedException.class,
            MissingServletRequestPartException.class,
            AsyncRequestTimeoutException.class
            // 下面这两个单独处理
            // MethodArgumentNotValidException.class,
            // BindException.class
    })
    @ResponseBody
    public BaseResponse<String> handleServletException(Exception e) {
        log.error("请求尚未到controller层", e);
        BaseResponse<String> response = new BaseResponse<>(ResultEnum.REQUEST_ERR, e.toString());
        // 进行国际化转换
        // i18nUtil.responseToI18n(response);
        return response;
    }

    /**
     * 参数校验绑定异常，spring boot版本过低的话，下面不能用BindException，两个异常没有继承关系，高版本才有
     *
     * @param e 异常
     * @return 返回结果
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public BaseResponse<String> bindException(BindException e) {
        // log.error("", e.getFieldError());
        // 将所有参数校验绑定错误返回
        BindingResult bindingResult = e.getBindingResult();
        StringBuilder msg = new StringBuilder();
        for (ObjectError error : bindingResult.getAllErrors()) {
            msg.append(", ");
            msg.append(error.getDefaultMessage() == null ? "" : error.getDefaultMessage());
        }

        BaseResponse<String> response = new BaseResponse<>(ResultEnum.VALIDATE_ERR, msg.substring(2));
        log.warn("{}", msg.substring(2));
        // 进行国际化转换
        // i18nUtil.responseToI18n(response);
        return response;
    }

    /**
     * 用户自定义异常
     *
     * @param e 异常
     * @return 返回结果
     */
    @ExceptionHandler(LoanException.class)
    public BaseResponse<String> LoanException(LoanException e) {
        log.warn("{}", e.getMessage());
        BaseResponse<String> response = new BaseResponse<>(e.getResultEnum());
        // 进行国际化转换
        // i18nUtil.responseToI18n(response);
        return response;
    }

    /**
     * 其他未知异常
     *
     * @param e 异常
     * @return 返回结果
     */
    @ExceptionHandler(Exception.class)
    public BaseResponse<String> otherException(Exception e) {
        log.error("", e);
        BaseResponse<String> response = new BaseResponse<>(ResultEnum.UNKNOWN_ERR, e.toString());
        // 进行国际化转换
        // i18nUtil.responseToI18n(response);
        return response;
    }

}