package com.nc.stock.project.stock.context;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import com.nc.stock.project.stock.config.KisConfig;
import com.nc.stock.project.stock.dto.BatchStatusDto;
import com.nc.stock.project.stock.facade.StockOrderService;
import com.nc.stock.project.stock.json.BatchJsonFileService;
import com.nc.stock.project.stock.listener.DefaultEventListener.AccomplishEvent;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MultiBatchOrderService {
    private final ThreadPoolTaskScheduler taskScheduler;
    private final BatchJsonFileService batchFileService;
    private final StockOrderService stockOrderService;
    
    //private final DefaultEventListener batchEventListener;
    private final ApplicationEventPublisher publisher;
    
    private final KisConfig kisConfig;
    // ...타 의존성 생략

    public MultiBatchOrderService(ThreadPoolTaskScheduler taskScheduler
							//, InfoJsonFileService infoJsonFileService
							, BatchJsonFileService batchFileService
							//,PriceService priceService
							//,StockAnalysisService stockAnalysisService
							,StockOrderService stockOrderService
							,ApplicationEventPublisher publisher
							,KisConfig kisConfig
							) {
				this.taskScheduler = taskScheduler;
				//this.infoJsonFileService = infoJsonFileService;
				this.batchFileService = batchFileService;
				//this.priceService = priceService;
				//this.stockAnalysisService = stockAnalysisService;
				this.stockOrderService = stockOrderService;
				this.publisher = publisher;
				this.kisConfig = kisConfig;
		}

	//내부 전용 Record 
	private record ProcessResult(boolean isSuccess, int amountPerOrder) {}

	//날짜 포맷
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    // 목표 시장 날짜 기준
    private final String MARKET_ZONE = "America/New_York";
    
    
    /**
     * [복구 로직] 서버 재기동 완료 후 5초 뒤에 데이터 자동 복구 엔진 가동
     * 애플리케이션 가동 완료 이벤트 발생 직후, 스레드를 차단(Sleep)하지 않고 5초 뒤 독립 실행되도록 예약합니다.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void scheduleRecoveryTask() {
        log.info("[시스템 가동 완료] 시스템이 안정화될 때까지 5초간 대기 후 배치를 복구합니다...");
        
        // 내장된 taskScheduler를 활용해 정확히 5초 뒤에 실제 복구 로직이 실행되도록 단발성 예약
        taskScheduler.schedule(
            () -> initAndRecoverProgress(), 
            java.time.Instant.now().plusSeconds(5)
        );
    }
    
    
    /**
     * [복구 로직] 서버 재기동 시 데이터 자동 복구 엔진 (다중 복구 지원)
     * 서버가 불시에 꺼졌다 켜지면 파일 시스템에서 백업을 읽어와 중단된 시점부터 스케줄러를 자동 재개합니다.
     * @PostConstruct 대신 서버 완료 이벤트를 구독하여 라이프사이클 충돌을 차단
     */
    //@PostConstruct
    //@org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void initAndRecoverProgress() {
        // 목표 시장 날짜 기준
    	String todayStr = LocalDate.now(ZoneId.of(MARKET_ZONE)).format(DATE_FORMATTER);
        
        // 1. 디스크에서 오늘 날짜에 중단된 모든 배치 백업 파일 목록을 읽어옵니다.
        // (batchFileService에 특정 날짜 접두사로 시작하는 파일 리스트를 가져오는 메서드가 필요합니다)
        List<BatchStatusDto> savedStatusList = 
                //batchFileService.readJsonFilesContaining(todayStr);
        		batchFileService.readJsonFilesAll();
        log.warn("[다중 배치 복구] {} 기준 오늘날짜:{} {}개 전략 파일 복구중..",MARKET_ZONE,todayStr, savedStatusList.size());
        
        for (BatchStatusDto savedStatus : savedStatusList) {
            try {
                if (savedStatus != null && !savedStatus.isCompleted()) {
                    String strategyFileName = savedStatus.getFileName();
                    log.warn("[다중 배치 복구] {} 전략: {} 파일 복구중..", strategyFileName,savedStatus.getBatchFileName());
                    
                    // 서버가 다운되어 있는 동안 흘러간 시간을 반영하여 진짜 남은 시간(초) 계산
                    long now = System.currentTimeMillis();
                    long totalLimitSeconds = (long) savedStatus.getTotalMinutes() * 60; // 최초 설정된 총 제한 시간(초)
                    long realElapsedSeconds = (now - savedStatus.getStartTimeMillis()) / 1000; // 실제 처음 시작 후 흐른 시간
                    long realRemainingSeconds = totalLimitSeconds - realElapsedSeconds; // 진짜 남은 시간

                    log.warn("======================================================================");
                    log.warn("[배치 시간 보정 복구] 기존 {}스텝 진행 중 중지 발생 감지.", savedStatus.getCurrentStep());
                    log.warn("[시간 모니터링] 최초 제한: {}초 | 가동 후 총 경과시간: {}초 | 다운타임 반영 실제 남은시간: {}초", 
                            totalLimitSeconds, realElapsedSeconds, realRemainingSeconds);
                    log.warn("======================================================================");
                    
                    // 마감 시간이 이미 지났는데 배치가 끝나지 않은 경우 예외 처리
                    if (realRemainingSeconds <= 0) {
                    	 log.error("=== [복구 실패] 서버가 다운된 사이 마감 제한 시간이 초과되었습니다. 배치를 재개할 수 없습니다. 미완료상태로 남겨둠. ===");
                    	 // 완료처리시에는 다시저장필요
                    	 //savedStatus.setCompleted(true);
                    	 //batchFileService.updateJsonFile(strategyFileName, savedStatus);
                    	 continue;
                    }

                    // 각 전략별 독립적인 컨텍스트 객체 생성 및 상태 복원
                    BatchJobContext context = new BatchJobContext(strategyFileName, todayStr);
                    
                    
                    
                    //파라미터도 context로  주입
                    context.getRetry().set(savedStatus.getRetry());
                    context.getTest().set(savedStatus.getTest1or0());  //0,1
                    context.getBuyOrSell().set(savedStatus.getBuyOrSell());//1,2
                    
                    context.getOrderRemainedAmountChanged().set(savedStatus.getOrderRemainedAmount()); //남은 주문 갯수 -> 실패횟수가 늘어날경우 나중에 점점 반복주기가 짧아짐
                    context.getIsCompleted().set(savedStatus.isCompleted()); 
                    
                    context.getTotalMinutes().set(savedStatus.getTotalMinutes());
                    context.getTotalExecuteCount().set(savedStatus.getTotalExecuteCount());
                    context.getOrderTotalAmount().set(savedStatus.getOrderTotalAmount());
                    
                    
                    
                    //context.getCurrentStep().set(savedStatus.getCurrentStep() + 1);  // 다음회차로 넘기지 않고 오류당시 회차에서 시작
                    context.getCurrentStep().set(savedStatus.getCurrentStep());
                    context.getRealFailCount().set(savedStatus.getRealFailCount()); // 기존 실패 횟수
                    context.getBatchStartTime().set(savedStatus.getStartTimeMillis()); // 기존 시작 시간

                    // '진짜 남은 시간'과 '남은 스텝 횟수'를 기반으로 압축된 새 주기를 계산  후 개별 엔진 가동
                    int totalExecuteCount = savedStatus.getTotalExecuteCount();
                    int remainingSteps = totalExecuteCount - savedStatus.getCurrentStep();
                    
                    if (remainingSteps > 0) {
                        long compressedPeriodSeconds = realRemainingSeconds / remainingSteps;
                        if (compressedPeriodSeconds < 10) compressedPeriodSeconds = 10; // 최소 간격 방어 :10초 이상
                        
                        log.info("[주기 압축 완료] 남은 수량 {}회를 {}초 안에 채우기 위해 새 주기를 {}초 간격으로 가속합니다.", 
                                remainingSteps, realRemainingSeconds, compressedPeriodSeconds);
                        
                        // 개별 컨텍스트를 주입하여 스케줄러 실행
                        //startSchedulerEngine(context, totalExecuteCount, savedStatus.getTotalMinutes(), savedStatus.getOrderTotalAmount(), compressedPeriodSeconds);
                        //context.getTotalExecuteCount().set(totalExecuteCount);
                        //context.getTotalMinutes().set(savedStatus.getTotalMinutes());
                        //context.getOrderTotalAmount().set(savedStatus.getOrderTotalAmount());
                        context.getDefaultPeriodSeconds().set(compressedPeriodSeconds);
                        startSchedulerEngineWithPeriod(context);
                    }
                }
            } catch (Exception e) {
                log.error("특정 배치 복구 중 실패 항목 패스: ", e);
            }
        }
    }

    /**
     * 개별 스케줄러 가동 엔진
     */
    
    /*
    public void startSchedulerEngine(BatchJobContext context, int totalExecuteCount, int totalMinutes, int orderTotalAmount, long periodSeconds) {
        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(() -> {
            
            // 실제 주기마다 실행될 비즈니스 로직 영역
            // 내부 로직 수행 ... (생략)
            
            // 비즈니스 로직 종료 후 백업 기록 호출 시 context를 넘겨줌
            saveBatchStatusToDisk(context, totalExecuteCount, totalMinutes, orderTotalAmount, false);
            
        }, Duration.ofSeconds(periodSeconds));
        
        context.getFutureRef().set(future);
    }
    /*


	
    
    
    /**
     * 최초 정각 정상 동적 스케줄링 실행 진입점 (다중 스케줄링 지원)
     * @param totalExecuteCount 목표 성공 횟수 (예: 100회)
     * @param totalHours  제한 시간 (예: 300분)
     */
    public void runOrderBatchTask(String strategyFileName, int totalExecuteCount, int totalMinutes, int orderTotalAmount,int buyOrSell,int retry, int test1or0) {
        String todayStr = LocalDate.now(ZoneId.of(MARKET_ZONE)).format(DATE_FORMATTER);
        
        // 1. 전역 변수 대신 이 스케줄러 전용 독립 컨텍스트(주머니) 생성
        //BatchJobContext context = new BatchJobContext(strategyFileName, todayStr);
        
        // 독립 컨텍스트 초기 생성 및 상태값 주입
        // Context 인스턴스 생성 (생성자 생성 위임)
        BatchJobContext context = new BatchJobContext(
						            strategyFileName
						            , todayStr
						            , retry
						            , test1or0  //0,1
						            , buyOrSell  //1,2
						            , totalMinutes
						            , totalExecuteCount
						            , orderTotalAmount
        						);
        
        
        //context.getIsCompleted().set(false); 
        //context.getRetry().set(retry);
        //context.getTest().set(test1or0);  //0,1
        //context.getBuyOrSell().set(buyOrSell); //1,2
        //context.getCurrentStep().set(1);        
        //context.getRealFailCount().set(0);      
        //context.getBatchStartTime().set(System.currentTimeMillis()); // 최초 시작 타임스탬프 발행
       
        //파라미터도 context로 옮김
        //context.getTotalMinutes().set(totalMinutes);
        //context.getTotalExecuteCount().set(totalExecuteCount);
        //context.getOrderTotalAmount().set(orderTotalAmount);
        //context.getOrderRemainedAmountChanged().set(orderTotalAmount); //남은 주문 갯수 -> 실패횟수가 늘어날경우 나중에 점점 반복주기가 짧아짐
        
        
        // 시간 및 횟수 조건 처리 규칙
        if (orderTotalAmount <= 5) { //5회 이하시에는 수행시간을 5회로 조정
            totalMinutes = orderTotalAmount;
            context.getTotalMinutes().set(totalMinutes);
        }
        
        if (orderTotalAmount <= totalExecuteCount) { //주문량 300 이하시 수행횟수를 주문량만큼으로 수정
            totalExecuteCount = orderTotalAmount;
            context.getTotalExecuteCount().set(totalExecuteCount);
        } else {
        	// 300 초과시에는 분할수행
        	// 여긴 완료.
        }
        
        long totalSeconds = (long) totalMinutes * 60;
        long defaultPeriodSeconds = totalSeconds / totalExecuteCount;  // 기본 정상 주기 계산 (예: 300분/300회 = 1분)
        if(defaultPeriodSeconds<2) {
        	defaultPeriodSeconds=2;
            log.warn("[경고] 계산된 주기가 0초 이하로 나와 최소 주기인 2초로 강제 조정합니다. 전략: {}", context.getStrategyFileName());
        } 
        context.getDefaultPeriodSeconds().set(defaultPeriodSeconds);
        
        // 파일명이 덮어써지지 않도록 context를 넘겨 첫 백업 생성
        saveBatchStatusToDisk(context);
        //saveBatchStatusToDisk(context, totalExecuteCount, totalMinutes, orderTotalAmount, false);
        
        // 스케줄러 엔진 구동 시 context 전달
        //startSchedulerEngineWithPeriod(context);
        startSchedulerEngineWithPeriodVersion3(context);
        //startSchedulerEngineWithPeriod(context, totalExecuteCount, totalMinutes, orderTotalAmount, defaultPeriodSeconds);
    }

    


    /**
     * [리팩토링] context를 전달받아 독립적으로 디스크에 백업하는 헬퍼 메서드
     */
    private void saveBatchStatusToDisk(BatchJobContext context) {
        
    	// 동일한 context(즉, 동일한 주식 종목 배치)에 대해서는 한 번에 한 스레드만 파일 쓰기를 허용
        synchronized (context) {
        	try {
                // 전역 변수가 아닌 인자로 받은 context 고유의 파일명을 사용
                BatchStatusDto statusDto = batchFileService.initJsonFile(context.getBatchFileName());
                
                long now = System.currentTimeMillis();
                long start = context.getBatchStartTime().get();
                long totalLimitSeconds = (long) context.getTotalMinutes().get() * 60;
                
                
                long elapsedSeconds = (now - start) / 1000;
                long remainingSeconds = totalLimitSeconds - elapsedSeconds;
                if (remainingSeconds < 0) remainingSeconds = 0;

                // 데이터 바인딩 시에도 context 내부의 고유 Thread-Safe 상태값 사용
                statusDto.setTotalExecuteCount(context.getTotalExecuteCount().get());  //totalExecuteCount
                statusDto.setTotalMinutes(context.getTotalMinutes().get());  //totalMinutes
                statusDto.setCompleted(context.getIsCompleted().get());  //isCompleted
                statusDto.setOrderTotalAmount(context.getOrderTotalAmount().get()); //orderTotalAmount
                
                statusDto.setBuyOrSell(context.getBuyOrSell().get());
                statusDto.setTest1or0(context.getTest().get());
                statusDto.setRetry(context.getRetry().get());
                
                statusDto.setCurrentStep(context.getCurrentStep().get());
                statusDto.setOrderRemainedAmount(context.getOrderRemainedAmountChanged().get());
                statusDto.setStartTimeMillis(start);
                statusDto.setElapsedSeconds(elapsedSeconds);
                statusDto.setRemainingSeconds(remainingSeconds);
                statusDto.setRealFailCount(context.getRealFailCount().get());
                statusDto.setFileName(context.getStrategyFileName());
                statusDto.setBatchFileName(context.getBatchFileName());
                
                // 고유 파일명으로 디스크 기록 : 디스크 및 메모리 멀티 캐시 동시 저장
                batchFileService.createJsonFile(context.getBatchFileName(), statusDto);

                log.info("[디스크 백업 기록] 전략: {} | 스텝: {}/{} | 누적 실패: {}회 | 진행시간: {}초 | 남은시간: {}초", 
                        context.getStrategyFileName(), context.getCurrentStep().get(), context.getTotalExecuteCount().get(), statusDto.getRealFailCount(), elapsedSeconds, remainingSeconds);

                if (context.getIsCompleted().get()) {
                	log.info("=최종완료 되었으며  saveBatchStatusToDisk 수행 마무리중. ");
                }
                
            } catch (IOException e) {
                log.error("배치 디스크 백업 실패! 전략명: {} ,{}" , context.getStrategyFileName(), e);
            }
        }
    	
    }
    
    
    
    
    
    
    
    
    

    
    /**
     * 공통 스케줄러 엔진 구동 (계산된 고정 주기를 주입받아 작동 - 다중 스케줄러  버전)
     */
    private void startSchedulerEngineWithPeriod(BatchJobContext context) {
        
    	int totalExecuteCount= context.getTotalExecuteCount().get();
    	//int totalMinutes= context.getTotalMinutes().get();
    	//int orderTotalAmount= context.getOrderTotalAmount().get();
    	long periodInSeconds= context.getDefaultPeriodSeconds().get();
    	
    	
    	log.info("[배치 가동] 전략: {} | 인터벌 {}초 간격으로 주행을 시작합니다. (목표 횟수: {}회, 현재 스텝: {})", 
                context.getStrategyFileName(), periodInSeconds, totalExecuteCount, context.getCurrentStep().get());
        
        ScheduledFuture<?> scheduledFuture = taskScheduler.scheduleAtFixedRate(() -> {
            // 1. 개별 컨텍스트(수첩)에서 현재 스텝을 안전하게 가져옴
            int step = context.getCurrentStep().get(); 

            if (step <= totalExecuteCount) {
                // 2. 비즈니스 로직 수행 (개별 수량 상태 사용)
                ProcessResult a = executeWithRetry(
                	context
                );
                
                // 결과 수량을 개별 컨텍스트에 안전하게 업데이트
                //context.getOrderRemainedAmountChanged().set(a.orderRemainedAmount());
                
                // 3. 마지막 단계 여부 확인
                boolean isLastStep = (step == totalExecuteCount);
                context.getIsCompleted().set(isLastStep);
                
                // 4. 실시간 시간 연산 후 파일 백업 (앞서 리팩토링한 메서드 호출)
                saveBatchStatusToDisk(context);

                // 5. 이 스케줄러만의 스텝을 안전하게 1 증가
                context.getCurrentStep().incrementAndGet();
                
                // 마지막 스텝을 방금 수행했다면 스케줄러를 스스로 종료 (안전장치 추가)
                if (isLastStep) {
                	String message = String.format("=== 모든 고정 주행 스텝을 정상 완료했습니다. ===");
                	terminateScheduler(context
                						,isLastStep
			                			,message
			                			,true //메일보냄?
			                			);
                }else if(context.isCircuitBreakerTriggered()) {
                	String message = String.format("실패횟수가 절반이상이므로 중단합니다 {}/{} ===", context.getRealFailCount().get(),context.getTotalExecuteCount().get());
                	terminateScheduler(context
                						,isLastStep
			                			,message
			                			,true //메일보냄?
			                			);
                }
                
            } else {
                // 목표 타겟 완수 시 해제 (초과 진입 방어 로직)
            	String message = String.format("=== 모든 고정 주행 스텝을 정상 완료했습니다. ===");
            	terminateScheduler(context
            						,true
		                			,message
		                			,true //메일보냄?
		                			);
            }
        }, Duration.ofSeconds(periodInSeconds));

        // 발급된 스케줄러 핸들러를 개별 컨텍스트에 보관하여 차후 스스로 종료할 수 있게 함
        context.getFutureRef().set(scheduledFuture);
    }
    
    
    
    /**
     * 스케줄러 안전 오프보딩 및 비동기 이벤트 트러거링
     */
    private void terminateScheduler(BatchJobContext context, boolean isSuccess, String message,boolean isSendingMail) {
        ScheduledFuture<?> future = context.getFutureRef().get();
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
            log.info("=== [배치 가동 테스크스케쥴링 종료] 전략: {} => {} ===", context.getStrategyFileName(), message);
            
            //if(isSuccess)log.info("=최종완료 되었으면 이제 이걸로 최종 보고서 메일 발송 ");
            if(isSendingMail)log.info("=실제 메일 보낼지 판단. ");
            AccomplishEvent event = new AccomplishEvent(
            		kisConfig.getMailMe()
        			, "제목: [" + context.getStrategyFileName() + "] 대량매수 배치 결과"
        			, "내용 : 주문량(" +context.getOrderTotalAmount().get() +"\n\n" +message
        			);
            publisher.publishEvent(event);
            //batchEventListener.sendMailEvent(event);
            
            /*
            // 핵심 엔터프라이즈 패턴: 결합도 제거를 위한 비동기 이벤트 발행
            eventPublisher.publishEvent(new BatchCompletionEvent(
                context.getStrategyFile(),
                context.getTotalExecuteCount().get(),
                context.getRealFailCount().get(),
                isSuccess,
                message
            ));
            */
        }
    }
    
    
    
    
    //[개선안] 단발성 연쇄 예약을 이용한  동적 스케줄러 엔진
    // 이건  총 실행 시간을 보장 하는 방법으로
    private void startSchedulerEngineWithPeriodVersion2(BatchJobContext context) {
        int step = context.getCurrentStep().get();
        int total = context.getTotalExecuteCount().get();

        // 1. 종료 조건 판단
        if (step > total) {
            completeBatchJob(context, "=== 모든 고정 주행 스텝을 정상 완료했습니다. ===", true);
            return;
        }
        if (context.isCircuitBreakerTriggered()) {
            completeBatchJob(context, "실패 횟수 누적으로 배치를 중단합니다.", true);
            return;
        }

        // 2. 비즈니스 로직 수행 및 상태 저장
        executeWithRetry(context);
        
        boolean isLastStep = (step == total);
        context.getIsCompleted().set(isLastStep);
        saveBatchStatusToDisk(context);
        
        // 3. 다음 회차를 위해 스텝 증가
        context.getCurrentStep().incrementAndGet();

        // 4. [핵심 수정] 작업 소요 시간을 제외하고 '최초 시작 시간' 기준으로 정밀 계산
        if (!isLastStep) {
            //java.time.Instant nextTargetInstant = context.calculateNextExecutionInstant();

            // 절대 시각(Instant)을 주입하여 정확한 시간에 실행 예약
            taskScheduler.schedule(
                () -> startSchedulerEngineWithPeriodVersion2(context), 
                context.calculateNextExecutionInstant()
            );
        } else {
            completeBatchJob(context, "=== 모든 고정 주행 스텝을 정상 완료했습니다. ===", true);
        }
    }
    
    
    // 개선안으로 수정할경우 사용할 종료 메서드 
    private void completeBatchJob(BatchJobContext context, String message, boolean isSendingMail) {
        log.info("=== [배치 종료] 전략: {} => {} ===", context.getStrategyFileName(), message);
        
        context.getIsCompleted().set(true);
        saveBatchStatusToDisk(context);
        
        if (isSendingMail) {
            publisher.publishEvent(new AccomplishEvent(
                kisConfig.getMailMe(),
                "제목: [" + context.getStrategyFileName() + "] 대량매수 배치 결과",
                "내용 : 주문량(" + context.getOrderTotalAmount().get() + ")\n\n" + message
            ));
        }
    }
    
    /**
     * [재시도 독립 분리형] 정상 스케줄은 절대 밀리지 않고, 실패 시 별도 비동기 스레드로 재시도하는 엔진
     */
    private void startSchedulerEngineWithPeriodVersion3(BatchJobContext context) {
        int step = context.getCurrentStep().get();
        int total = context.getTotalExecuteCount().get();

        // 1. 종료 조건 판단
        if (step > total) {
        	context.getCurrentStep().decrementAndGet();// 완료시 -1
            completeBatchJob(context, "=== 모든 고정 주행 스텝을 정상 완료했습니다. ===", true);
            return;
        }
        if (context.isCircuitBreakerTriggered()) {
            completeBatchJob(context, "실패 횟수 누적으로 배치를 중단합니다.", true);
            return;
        }

        // 2. 이번 회차의 주문 수량 계산
        int amountPerOrder = context.calculateAmountPerOrder();
        
        // 3. 1회차 본 주문 송신
        ProcessResult result = processBusinessLogic(context, amountPerOrder);

        if (result.isSuccess()) {
            // [경우 A] 본 주문 성공 시 -> 수량 차감 후 정상 전진
            log.info(" -> [{}/{}번째 작업 성공] {}개 주문 완료", step,total, amountPerOrder);
            context.orderRemainedAmountChangedMinus(amountPerOrder);
            saveBatchStatusToDisk(context);
        } else {
            // [경우 B] 본 주문 실패 시 -> ★메인 스레드를 붙잡지 않고, 별도 비동기 스레드로 재시도 위임
            log.warn(" -> [{}번째 작업 1차 실패] 메인 스케줄은 정각 유지를 위해 전진하며, 재시도는 백그라운드 스레드로 위임합니다.", step);
            
            // 스프링의 taskScheduler를 사용해 '5초 뒤'에 완전히 독립된 스레드에서 실행되도록 던짐
            int initialRetryCount = 1;
            taskScheduler.schedule(
                () -> executeBackgroundRetry(context, step, amountPerOrder, initialRetryCount),
                Instant.now().plusSeconds(5)
            );
        }

        boolean isLastStep = (context.getCurrentStep().get() >= total);
        if (!isLastStep) {

            // 4. [핵심] 성공/실패 여부와 상관없이 메인 스케줄은 즉시 다음 스텝으로 이동 및 예약
            context.getCurrentStep().incrementAndGet();
            
            // 본 주문이 실패해서 백그라운드에서 재시도가 돌고 있더라도, 메인 스케줄은 원래 정각 시간에 정확히 실행됨
            taskScheduler.schedule(
                () -> startSchedulerEngineWithPeriodVersion3(context), 
                context.calculateNextExecutionInstant()
            );
        } else {
        	
        	 context.getCurrentStep().incrementAndGet();
             
             // 본 주문이 실패해서 백그라운드에서 재시도가 돌고 있더라도, 메인 스케줄은 원래 정각 시간에 정확히 실행됨
             taskScheduler.schedule(
                 () -> startSchedulerEngineWithPeriodVersion3(context), 
                 context.calculateNextExecutionInstant()
             );
        	
            // 마지막 스텝인 경우 백그라운드 재시도가 끝날 때까지 대기 후 종료 처리가 필요할 수 있으므로 상태만 저장
            // 배치의 완료란 정해진 회차를 실행했음을 의미
        	//context.getIsCompleted().set(isLastStep);
        	//saveBatchStatusToDisk(context);
        	//context.getCurrentStep().decrementAndGet();// 완료시 -1
        	//completeBatchJob(context, "=== 모든 회차를 정상 주행 완료했습니다. ===", true);
            
        }
    }
    
    /**
     * [완전 분리] 메인 스케줄러와 독립된 별도의 스레드 풀에서 돌아가는 재시도 전용 메서드
     */
    private void executeBackgroundRetry(BatchJobContext context, int failedStep, int amountPerOrder, int retryCount) {
        int maxRetries = context.getRetry().get();
        log.info("   [백그라운드 재시도 스레드 가동] {}번째 작업의 {}차 재시도 중... (목표 수량: {}개)", failedStep, retryCount, amountPerOrder);

        ProcessResult result = processBusinessLogic(context, amountPerOrder);

        if (result.isSuccess()) {
            log.info("   -> [백그라운드 재시도 성공] {}번째 작업이 {}차 재시도 끝에 성공했습니다. {}개 차감합니다.", failedStep, retryCount, amountPerOrder);
            context.orderRemainedAmountChangedMinus(amountPerOrder); // 성공한 시점에 안전하게 수량 차감
            saveBatchStatusToDisk(context);
        } else {
            if (retryCount < maxRetries) {
                // 아직 재시도 기회가 남았다면 또다시 5초 뒤에 별도 스레드로 예약 (스레드 반환 후 재호출)
                taskScheduler.schedule(
                    () -> executeBackgroundRetry(context, failedStep, amountPerOrder, retryCount + 1),
                    Instant.now().plusSeconds(5)
                );
            } else {
                // 백그라운드 재시도까지 최종적으로 전부 실패한 경우
                context.getRealFailCount().incrementAndGet();
                log.error("   -> [백그라운드 재시도 최종 실패] {}번째 작업은 모든 재시도 후에도 결국 실패했습니다.", failedStep);
                saveBatchStatusToDisk(context);
            }
        }
    }
    
    /*
    
    // 새로운 방법에 대한것  이는  실행주기별로  한번씩 실행되는 방법임 
    private void startSchedulerEngineWithPeriod(BatchJobContext context) {
        // 1. 종료 조건 먼저 명확하게 판단
        int step = context.getCurrentStep().get();
        int total = context.getTotalExecuteCount().get();

        // [종료 조건 1] 목표 완료 및 초과 진입 방어
        if (step > total) {
            completeBatchJob(context, "=== 모든 고정 주행 스텝을 정상 완료했습니다. ===", true);
            return;
        }
        // [종료 조건 2] 서킷 브레이커 발동 (실패 과다)
        if (context.isCircuitBreakerTriggered()) {
            completeBatchJob(context, "실패 횟수 누적으로 배치를 중단합니다.", true);
            return;
        }

        // 2. 비즈니스 로직 수행 및 상태 저장
        executeWithRetry(context);
        
        boolean isLastStep = (step == total);
        context.getIsCompleted().set(isLastStep);
        saveBatchStatusToDisk(context);
        
        // 3. 다음 회차를 위해 스텝 증가
        context.getCurrentStep().incrementAndGet();

        // 4. [핵심] 아직 끝난 게 아니라면, 다음 실행을 "딱 한 번만" 예약함
        if (!isLastStep) {
            long periodInSeconds = context.getDefaultPeriodSeconds().get(); // 필요시 여기서 주기 동적 변경 가능!
            
            // 자기 자신(메서드)을 다음 주기에 다시 실행하도록 1회성 예약
            taskScheduler.schedule(
                () -> startSchedulerEngineWithPeriod(context), 
                Instant.now().plusSeconds(periodInSeconds)
            );
        } else {
            // 마지막 스텝을 방금 수행 완료한 경우
            completeBatchJob(context, "=== 모든 고정 주행 스텝을 정상 완료했습니다. ===", true);
        }
    }


    
   
    */
    
    
    
	/*
	 * 로직을 실행하고, 실패 시 최대 maxRetries번 재시도하는 메서드
	 */
    private ProcessResult executeWithRetry(BatchJobContext context) {
    	int step=context.getCurrentStep().get();
    	int maxRetries=context.getRetry().get();
    	//int totalExecuteCount=context.getTotalExecuteCount().get();
    	//int orderTotalAmount=context.getOrderTotalAmount().get();
    	//int orderRemainedAmount=context.getOrderRemainedAmountChanged().get();
    	
    	//int maxRetries = 1; 
        int retryIntervalMillis = 5000; //재시도는 5초 후에
        

        int amountPerOrder = context.calculateAmountPerOrder();
        

        for (int retry = 0; retry <= maxRetries; retry++) {
            if (retry > 0) {
                log.info("   -> [재시도 {}/{}] {}번째 작업 다시 시도 중...", retry, maxRetries, step);
            } else {
                log.info("[{} / {}회차 작업 시작]", step, context.getCurrentStep().get());  
            }

            //boolean isSuccess = processBusinessLogic(step,totalExecuteCount,orderTotalAmount);

            //ProcessResult result =processBusinessLogic(context,step,totalExecuteCount,orderTotalAmount,orderRemainedAmount);
            ProcessResult result =processBusinessLogic(context,amountPerOrder);

            boolean isSuccess = result.isSuccess();
            if (isSuccess) {
                log.info(" -> [{}번째 작업 성공]", step);
                // 성공시 결과 수량을 개별 컨텍스트에 업데이트
                context.orderRemainedAmountChangedMinus(amountPerOrder);
                return result; // 성공했으므로 재시도 루프 탈출
            }

            // 실패했고, 아직 재시도 기회가 남았다면 대기 후 다시 돌림
            if (retry < maxRetries) {
                try {
                    Thread.sleep(retryIntervalMillis); 
                } catch (InterruptedException e) {  //강제 종료 시
                    Thread.currentThread().interrupt();
                    return result;
                }
            } else {
            	context.getRealFailCount().incrementAndGet();
                log.warn(" -> [각작업당 최종 실패] {}번째 작업은 반복 재시도 후에도 통과하지 못했습니다.", step);
                return result;
            }
        }
		return new ProcessResult(false,0);
    }
    
    

    /**
     * 결과값을 반환하도록 수정한 핵심 비즈니스 로직
     * @return 통과 여부 (true = 성공, false = 실패)
     */
    private ProcessResult processBusinessLogic(BatchJobContext context,int amountPerOrder) {
    	int step = context.getCurrentStep().get();
    	int totalExecuteCount = context.getTotalExecuteCount().get();
    	int orderTotalAmount = context.getOrderTotalAmount().get();
    	int orderRemainedAmount = context.getOrderRemainedAmountChanged().get();
    	
    	log.info("[작업 시작] {}번째 실행 시도 중...{}-총횟수, {}-총주문갯수, {}-남은주문갯수, {}-주문당주문갯수"
       		 ,step,totalExecuteCount,orderTotalAmount,orderRemainedAmount,amountPerOrder);  //  \n
       
    	
        try {
        	boolean orderExcuted= false;
        	
        	// -------------------------------------------------------------
            // 여기에 실제 실행할 비즈니스 로직을 작성합니다.
            // 예시: 외부 API 호출, DB 저장 등
            // -------------------------------------------------------------
            

            // 검증 로직 결과에 따라 true 또는 false 반환
            boolean validationResult = checkYourCondition(); 
            
            //int buyOrSell =1; //SELL=2 "BUY" =1
            // 작성된 파일 내용을 바탕으로 매매 실행 함수
        	orderExcuted = stockOrderService.buyOrSellExecutions(
        								  context.getStrategyFileName() 
        								, context.getBuyOrSell().get() 
        								, amountPerOrder 
        								, context.getTest().get()
        								);
            if(orderExcuted) {
            	// 결과 수량을 개별 컨텍스트에 업데이트
            	//context.orderRemainedAmountChangedMinus(amountPerOrder);
            	//orderRemainedAmount =orderRemainedAmount-amountPerOrder;
            	//context.getOrderRemainedAmountChanged().set(orderRemainedAmount);

                return new ProcessResult(orderExcuted,amountPerOrder);
            }else {
                return new ProcessResult(orderExcuted,0);
            }
            
            //return orderExcuted; 
            
        } catch (Exception e) {
            // 에러 발생 시 실패(false) 처리하여 카운트 제외
            log.error("로직 실행 중 예외 발생:{} " , e.getMessage());
            return new ProcessResult(false,0);
        }
    }
    
    // 예시용 검증 메서드 (실제 프로젝트의 검증 조건으로 대체하세요)
    private boolean checkYourCondition() {
        // 무조건 통과한다고 가정 (실패 테스트 시 false 반환하도록 제어 가능)
    	//return false;
    	return true; 
    }
}
