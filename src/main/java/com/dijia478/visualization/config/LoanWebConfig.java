package com.dijia478.visualization.config;

import com.dijia478.visualization.interceptor.RequestInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 定制SpringMVC的一些功能
 *
 * @author dijia478
 * @date 2023/9/7
 */
@Configuration
public class LoanWebConfig implements WebMvcConfigurer {

    /**
     * 配置拦截器
     * @param registry 相当于拦截器的注册中心
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/calculator/input.css",
                        "/calculator/common.js",
                        "/calculator/QR_code.png");
    }

}
