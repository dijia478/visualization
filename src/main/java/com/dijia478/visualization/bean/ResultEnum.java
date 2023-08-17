package com.dijia478.visualization.bean;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应码枚举类
 *
 * @author dijia478
 * @date 2023-8-9 16:53:13
 */
@Getter
@AllArgsConstructor
public enum ResultEnum {

    /** 成功 */
    SUCCESS(200, "成功"),

    /** 失败 */
    FAILURE(600, "错误"),

    /** 未知异常 */
    UNKNOWN_ERR(601, "未知异常"),

    /** 参数校验失败 */
    VALIDATE_ERR(602, "参数校验失败"),

    /** json处理异常 */
    JSON_ERR(603, "json处理异常"),

    /** 数据不存在 */
    NOT_EXIST(604, "数据不存在"),

    /** 请求错误 */
    REQUEST_ERR(605, "请求错误"),

    PREPAYMENT_MONTH_TOO_BIG(701, "提前还款时的还款期数超过范围"),

    REPAYMENT_TOO_BIG(702, "提前还款时的还款金额超过范围");

    private final Integer code;

    private final String message;

}