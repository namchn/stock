package com.nc.stock.common.web.interceptor;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;


import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DynamicRateLimitInterceptor implements HandlerInterceptor {

    private final Runtime runtime = Runtime.getRuntime();
    
    /* deprecated
    // 기본 버킷 (평소에는 초당 100개까지 넉넉하게 허용) 
    private final Bucket normalBucket = Bucket.builder()
            .addLimit(Bandwidth.classic(100, Refill.intervally(100, Duration.ofSeconds(1))))
            .build();
    */
    
    // 빌더 패턴을 이용한 명시적 선언 방식
    private final Bucket globalBucket = Bucket.builder()
            .addLimit(Bandwidth.builder()
                    .capacity(30)
                    //.refillIntervally(30, Duration.ofSeconds(1)) //정확히 1초가 지나는 순간에 30개
                    .refillGreedy(30, Duration.ofSeconds(1)) //1초 동안 30개가 차오르도록 잘게 
                    .build())
            .build();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        
    	// -- 추가한 부분 -- //
        // 1. 실시간 JVM 메모리 상태 계산 (스왑이 없으므로 RAM 잔여량이 생명줄입니다)
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;
        
        double memoryUsageRatio = (double) usedMemory / maxMemory;

        // 2. 임계치 조건문: 메모리 사용량이 85%를 넘으면 무조건 요청을 차단하여 OOM 방지 (오버아웃메모리)
        if (memoryUsageRatio > 0.85) {
            log.warn("서버 메모리 위험!! (사용률: {}%). 요청을 강제 차단합니다.", String.format("%.2f", memoryUsageRatio * 100));
            return throwTooManyRequests(response);
        }
        // -- 추가한 부분 -- //
        

        // 3. 평소에는 셋팅된 버킷 용량대로 처리 // 토큰 1개 소비
        if (globalBucket.tryConsume(1)) {
            return true; // 프리티어 한도 내이므로 통과 (컨트롤러 실행)
        }

        return throwTooManyRequests(response);
    }

    private boolean throwTooManyRequests(HttpServletResponse response) throws Exception {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\": \"서버 부하를 방지하기 위해 요청이 제한되었습니다(30회/1초). 잠시 후 다시 시도해 주세요.\"}");
        return false;
    }
}
