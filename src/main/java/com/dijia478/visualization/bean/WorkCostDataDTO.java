package com.dijia478.visualization.bean;

import lombok.Data;

import javax.validation.constraints.Max;

/**
 * @author dijia478
 * @date 2023/9/26
 */
@Data
public class WorkCostDataDTO {

    @Max(value = 20000, message = "你是中彩票了吗？")
    private String num1;
    @Max(value = 24, message = "一天能有这么长？")
    private String num2;
    @Max(value = 24, message = "一天能有这么长？")
    private String num3;
    @Max(value = 24, message = "一天能有这么长？")
    private String num4;
    private String num5;
    private String num6;
    private String num7;
    private String num8;
    private String num9;
    private String num10;
    private String num11;
    private String result;
    private String ip;
}
