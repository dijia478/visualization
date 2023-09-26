package com.dijia478.visualization.service;

import java.io.IOException;

/**
 * 工作性价比
 *
 * @author dijia478
 * @date 2023/9/26
 */
public interface WorkCostService {

    /**
     * 获取当前分数所占总人数的百分比
     *
     * @param result
     * @return
     */
    String getPercent(String result) throws IOException;

}
