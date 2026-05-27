package com.nc.stock.common.web.webConfig;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.nc.stock.common.web.interceptor.DynamicRateLimitInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    //private final RateLimitInterceptor rateLimitInterceptor;
    private final DynamicRateLimitInterceptor rateLimitInterceptor;

    public WebConfig(DynamicRateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**") // 모든 주소(API, 정적 리소스 등)에 대해 1차 필터링
                .excludePathPatterns("/error"); // 스프링 내부 에러 페이지는 제외
    }
}