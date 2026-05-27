package com.nc.stock.project.stock.token;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.nc.stock.common.email.EmailService;
import com.nc.stock.project.stock.config.KisConfig;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
public class TokenScheduler {

    private final TokenService tokenService;
    private final EmailService emailService;
    private final KisConfig kisConfig; 
    
    
    // 서버 시작 시 1회 실행
    @PostConstruct
    public void init() {
    	
    	String initAccessToken =  tokenService.getInitAccessToken();
    	if(initAccessToken.length()>0) {
    		tokenService.initToken();
    		log.info("=initAccessToken=");
    	}else {
            tokenService.refreshToken();
            log.info("=refreshToken=");
    	}
    	
    }

    // 1시간마다 실행
    //@Scheduled(fixedDelay = 60 * 60 * 1000)
    // 서버 시작 1시간 뒤부터 실행
    //@Scheduled(initialDelay = 60 * 60 * 1000,fixedDelay = 60 * 60 * 1000)
    @Scheduled(cron = "0 50 21 * * *", zone = "Asia/Seoul")
    public void refresh() {
        tokenService.refreshToken();
        log.info("=refreshToken@Scheduled=");
        //emailService.sendEmail(kisConfig.getMailMe(), "토큰: [" + "refresh" + "]", "내용 생략");

    }
    
}