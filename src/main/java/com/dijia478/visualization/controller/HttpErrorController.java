package com.dijia478.visualization.controller;

import com.dijia478.visualization.bean.BaseResponse;
import com.dijia478.visualization.bean.LoanException;
import com.dijia478.visualization.bean.ResultEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * @author dijia478
 * @date 2023/9/13
 */
@RestController
public class HttpErrorController {

    @Autowired
    private BasicErrorController basicErrorController;

    /**
     * 接管/error的处理
     * 由于用的是精确匹配, 所以会覆盖系统默认的处理
     *
     * @param request
     * @param response
     * @return
     */
    @RequestMapping(value = "/error", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public BaseResponse<ResponseEntity<Map<String, Object>>> error(HttpServletRequest request, HttpServletResponse response){
        response.setStatus(HttpStatus.OK.value());
        ResponseEntity<Map<String, Object>> errorDetail = basicErrorController.error(request);
        return new BaseResponse<>(ResultEnum.REQUEST_ERR, errorDetail);
    }

}
