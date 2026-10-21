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

    REPAYMENT_TOO_BIG(702, "提前还款时的还款金额超过范围"),

    RATE_CONFIG_ERR(703, "请检查利率类型、重定价周期和重定价日期"),

    PAYMENT_TOO_SMALL(704, "原月供不足以覆盖新利率下的利息，请选择期限不变方案"),

    SHORTENING_UNAVAILABLE(706, "保持原月供无法缩短新还款方案的期限，请选择期限不变或增加提前还款金额"),

    LPR_HISTORY_UNAVAILABLE(705, "该放款日期早于离线LPR数据，请填写合同加点或选择固定利率"),

    DATE_FORMAT_ERR(801, "请检查日期格式及提前还款日期，提前还款需在首次还款日至贷款到期日之间");

    private final Integer code;

    private final String message;

}