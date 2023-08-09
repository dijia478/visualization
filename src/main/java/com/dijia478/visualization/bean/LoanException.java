package com.dijia478.visualization.bean;

import lombok.Getter;

/**
 * 贷款相关异常
 *
 * @author dijia478
 * @date 2023-8-9 16:58:28
 */
@Getter
public class LoanException extends RuntimeException {

    private final ResultEnum resultEnum;

    public LoanException(ResultEnum resultEnum) {
        super(resultEnum.getMessage());
        this.resultEnum = resultEnum;
    }

}