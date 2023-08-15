package com.dijia478.visualization.bean;

import lombok.Data;
import javax.validation.constraints.*;

/**
 * 前端请求的参数
 *
 * @author dijia478
 * @date 2023/8/9
 */
@Data
public class LoanDTO {

    @NotNull(message = "贷款额度不能为空")
    @Min(value = 1, message = "贷款额度不能小于1万元")
    private Integer amount;

    @NotNull(message = "贷款期限不能为空")
    @Max(value = 30, message = "贷款期限不能大于30年")
    @Min(value = 1, message = "贷款期限不能小于1年")
    private Integer year;

    @NotNull(message = "年利率不能为空")
    @Min(value = 0, message = "年利率不能小于0")
    private Double rate;

    @NotNull(message = "还款方式不能为空")
    private Integer type;

    @NotNull(message = "提前还款不能为空")
    private Integer prepayment;

}
