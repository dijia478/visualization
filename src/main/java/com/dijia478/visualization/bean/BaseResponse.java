package com.dijia478.visualization.bean;

import lombok.Data;

/**
 * 基础返回对象
 *
 * @author dijia478
 * @date 2023-8-9 16:52:15
 */
@Data
public class BaseResponse<T> {

    /** 响应码 */
    private Integer code;

    /** 响应信息 */
    private String message;

    /** 响应数据 */
    private T data;

    public BaseResponse() {
        this(ResultEnum.SUCCESS, null);
    }

    public BaseResponse(T data) {
        this(ResultEnum.SUCCESS, data);
    }

    public BaseResponse(ResultEnum resultEnum) {
        this(resultEnum, null);
    }

    public BaseResponse(ResultEnum resultEnum, T data) {
        this.code = resultEnum.getCode();
        this.message = resultEnum.getMessage();
        this.data = data;
    }

}