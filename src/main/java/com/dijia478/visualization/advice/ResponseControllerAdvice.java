package com.dijia478.visualization.advice;


import com.dijia478.visualization.bean.BaseResponse;
import com.dijia478.visualization.bean.LoanException;
import com.dijia478.visualization.bean.ResultEnum;
import com.dijia478.visualization.util.I18nUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 全局响应体统一处理类
 *
 * @author dijia478
 * @date 2023-8-9 16:58:42
 */
@RestControllerAdvice(basePackages = {"com.dijia478.visualization.controller"})
@Slf4j
public class ResponseControllerAdvice implements ResponseBodyAdvice<Object> {

    @Autowired
    private I18nUtil i18nUtil;

    /**
     * 此方法用来判断，哪些接口的返回值需要被包装，哪些不需要
     *
     * @param methodParameter
     * @param aClass
     * @return
     */
    @Override
    public boolean supports(MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> aClass) {
        // 如果返回的对象是baseResponse，则不需要本类处理
        return !methodParameter.getGenericParameterType().equals(BaseResponse.class);
    }

    /**
     * 此方法用来包装接口的返回值，全部统一返回{@link BaseResponse}
     *
     * @param o
     * @param methodParameter
     * @param mediaType
     * @param aClass
     * @param serverHttpRequest
     * @param serverHttpResponse
     * @return
     */
    @Override
    public Object beforeBodyWrite(Object o, MethodParameter methodParameter, MediaType mediaType, Class<? extends HttpMessageConverter<?>> aClass, ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse) {
        // String类型不能直接返回，所以要进行些特别的处理
        BaseResponse<Object> response = new BaseResponse<>(o);
        // log.info("resp: {}", response);
        // 进行国际化转换，根据需要传入第三个参数
        i18nUtil.responseToI18n(response);

        if (methodParameter.getGenericParameterType().equals(String.class)) {
            try {
                // 将baseResponse转换为json字符串响应给前端
                return new ObjectMapper().writeValueAsString(response);
            } catch (JsonProcessingException e) {
                throw new LoanException(ResultEnum.JSON_ERR);
            }
        }
        // 将原本的数据包装在ResultVO里
        return response;
    }

}