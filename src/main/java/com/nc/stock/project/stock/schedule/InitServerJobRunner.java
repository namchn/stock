package com.nc.stock.project.stock.schedule;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import com.nc.stock.common.securityToken.SecurityTokenGenerator;
import com.nc.stock.project.stock.config.KisConfig;
import com.nc.stock.project.stock.facade.AccountAnalysisService;
import com.nc.stock.project.stock.facade.StockAnalysisService;
import com.nc.stock.project.stock.listener.DefaultEventListener.AccomplishEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class InitServerJobRunner {

	// 서버가 실제 준비 완료된 시점을 저장할 변수
    private LocalDateTime serverStartTime;
    private final SecurityTokenGenerator securityTokenGenerator;
    
    private final ThreadPoolTaskScheduler taskScheduler;
    private final StockAnalysisService stockAnalysisService;
    private final AccountAnalysisService accountAnalysisService;
    
    private final KisConfig kisConfig;
    
    //private final DefaultEventListener defaultEventListener;
    private final ApplicationEventPublisher publisher;
    
    
    public InitServerJobRunner(
    						SecurityTokenGenerator securityTokenGenerator
    						,ThreadPoolTaskScheduler taskScheduler
    						,StockAnalysisService stockAnalysisService
    						,AccountAnalysisService accountAnalysisService
    						,ApplicationEventPublisher publisher
    						,KisConfig kisConfig
    						) {
    	this.securityTokenGenerator = securityTokenGenerator;
        this.taskScheduler = taskScheduler;
        this.stockAnalysisService = stockAnalysisService;
        this.accountAnalysisService = accountAnalysisService;
        this.publisher = publisher;
        this.kisConfig = kisConfig;
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        this.serverStartTime = LocalDateTime.now();
        log.info("[서버 기동 완료] 경과 시간 측정을 시작합니다.");
        
    	// data.setDate(LocalDateTime.now().toString()); // 현재 시간 저장
 		// 1. 서울 시간대 및 포맷 정의
 		ZoneId seoulZone = ZoneId.of("Asia/Seoul");
 		DateTimeFormatter saveFormatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
 		// 2. 현재 서울 시간을 기준으로 포맷 변환
 		String formattedDate = ZonedDateTime.now(seoulZone).format(saveFormatter);
 		log.info("[서버 기동 완료] 시간 : {}",formattedDate); 
 		
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleTasks() {
        Instant now = Instant.now();

        // 1. 재기동 20s 후 실행 예약
        //Instant oneMinuteDelay = now.plus(1, ChronoUnit.MINUTES);
        taskScheduler.schedule(this::runAfterOne, now.plus(20, ChronoUnit.SECONDS));
        // 2. 재기동 40s 후 실행 예약
        //Instant twoMinutesDelay = now.plus(2, ChronoUnit.MINUTES);
        taskScheduler.schedule(this::runAfterTwo, now.plus(40, ChronoUnit.SECONDS));
        // 3. 재기동 60s 후 실행 예약
        //Instant threeMinutesDelay = now.plus(3, ChronoUnit.MINUTES);
        taskScheduler.schedule(this::runAfterThree, now.plus(60, ChronoUnit.SECONDS));
    }

    // 정해진시간 후 실행될 메서드
    private void runAfterOne() {
        log.info("서버 재기동 20s 후 실행 완료");
        //전일자 기준 200일선 계산스케쥴링 
       stockAnalysisService.calculate200DayMovingAverage("TQQQ","TQQQ", "NAS",1); //"TQQQ","NAS" //하루전날 데이터수집   
       //stockAnalysisService.calculate200DayMovingAverage("SPYM","SPYM", "AMS",1); //"SPYM","AMS" //하루전날 데이터수집
        
       
       //StringBuffer sb = new StringBuffer(securityTokenGenerator.generateSecureToken());
       log.warn("44글자 암호화 토큰 : {}", securityTokenGenerator.generateSecureTimeToken());
       //log.warn("보안 토큰 생성 완료 (Prefix: {}***)", securityTokenGenerator.generateSecureToken().substring(0, 6)); 
    }
    // 정해진시간 후 실행될 메서드
    private void runAfterTwo() {
    	log.info("서버 재기동 40s 후 실행 완료");
    	//주문 가능 금액/수량 확인
    	accountAnalysisService.SetOrderAbleAmount("TQQQ", "TQQQ","0.0", "NASD"); //NASD : 나스닥 / NYSE : 뉴욕 / AMEX : 아멕스
    	//accountAnalysisService.SetOrderAbleAmount("SPYM", "SPYM","0.0", "AMEX"); //NASD : 나스닥 / NYSE : 뉴욕 / AMEX : 아멕스
    }
    // 정해진시간 후 실행될 메서드
    private void runAfterThree() {
    	log.info("서버 재기동 60s 후 실행 완료");
    	//계좌 매도 가능 금액/수량 확인
    	accountAnalysisService.setBalanceAmount("TQQQ", "TQQQ","0.0", "NASD"); //NASD : 나스닥 / NYSE : 뉴욕 / AMEX : 아멕스
    	//accountAnalysisService.setBalanceAmount("SPYM", "SPYM","0.0", "AMEX"); //NASD : 나스닥 / NYSE : 뉴욕 / AMEX : 아멕스
    	

    	AccomplishEvent event = new AccomplishEvent(
        		kisConfig.getMailMe()
        		, "제목: [" + "재기동" + "] 1분후 완료"
        		, "이벤트 리스너 만듬"
        		);
        publisher.publishEvent(event);
        //defaultEventListener.sendMailEvent(event);
        
    	//emailService.sendEmail(kisConfig.getMailMe(), "제목: [" + "재기동" + "] 1분후 완료", "내용 생략");
    }
    
    
    
    // 1분(60,000ms)마다 주기적으로 실행
    //@Scheduled(fixedRate = 60000)
    public void logUptime() {
        // 서버가 켜지기 전(초기화 단계) 예외 처리
        if (serverStartTime == null) {
            return;
        }

        // 현재 시간과 기동 시점의 차이 계산
        Duration duration = Duration.between(serverStartTime, LocalDateTime.now());
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.toSeconds() % 60;

        // 로그 출력 (예: 서버 가동 중... [경과 시간: 0시간 1분])
        log.info("[서버 상태] 가동 중... [경과 시간: {}시간 {}분 {}초]", hours, minutes,seconds);
    }
}