package com.nc.stock.project.stock.context;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import com.nc.stock.common.email.EmailService;
import com.nc.stock.project.stock.dto.BatchStatusDto;
import com.nc.stock.project.stock.facade.StockAnalysisService;
import com.nc.stock.project.stock.facade.StockOrderService;
import com.nc.stock.project.stock.json.BatchJsonFileService;
import com.nc.stock.project.stock.json.InfoJsonFileService;
import com.nc.stock.project.stock.service.PriceService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class BatchOrderService {
	
	private final ThreadPoolTaskScheduler taskScheduler;
    private final InfoJsonFileService infoJsonFileService;
    private final BatchJsonFileService batchFileService;
    private final PriceService priceService; 
    private final StockAnalysisService stockAnalysisService;
    private final StockOrderService stockOrderService;
    private final EmailService emailService;
    
    public BatchOrderService(ThreadPoolTaskScheduler taskScheduler
    						, InfoJsonFileService infoJsonFileService
    						, BatchJsonFileService batchFileService
    						,PriceService priceService
    						,StockAnalysisService stockAnalysisService
    						,StockOrderService stockOrderService
    						,EmailService emailService
    						) {
        this.taskScheduler = taskScheduler;
        this.infoJsonFileService = infoJsonFileService;
        this.batchFileService = batchFileService;
        this.priceService = priceService;
        this.stockAnalysisService = stockAnalysisService;
        this.stockOrderService = stockOrderService;
        this.emailService = emailService;
    }

    
	//내부 전용 Record 
	private record ProcessResult(boolean isSuccess, int orderRemainedAmount) {}

	//private int orderRemainedAmountChanged = 0;
	private final AtomicInteger orderRemainedAmountChanged = new AtomicInteger(1);
    
    //반복횟수
    //private final int retry =1;
    
    // 배치를 관리할 고유 파일명 설정
    private String strategyFile = null;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
	String todayStr = LocalDate.now(ZoneId.of("America/New_York")).minusDays(0).format(DATE_FORMATTER); //Asia/Seoul
    private final String BATCH_FILE_NAME = "order_batch_file"+"_"+todayStr;
    
    // 상태 관리를 전역 Atomic 변수로 승격 (재기동 복구 및 멀티스레드 반영을 위함)

    private final AtomicInteger retry = new AtomicInteger(1);
    private final AtomicInteger test = new AtomicInteger(1);
    private final AtomicInteger buyOrSell = new AtomicInteger(1);
    private final AtomicInteger currentStep = new AtomicInteger(1);
    private final AtomicInteger realFailCount = new AtomicInteger(0); 
    private final AtomicLong batchStartTime = new AtomicLong(0); // 최초 시작 시간 보관용
    private final AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

    

    /**
     * [복구 로직] 서버 재기동 시 데이터 자동 복구 엔진
     * 서버가 불시에 꺼졌다 켜지면 파일 시스템에서 백업을 읽어와 중단된 시점부터 스케줄러를 자동 재개합니다.
     */
    //@PostConstruct
    public void initAndRecoverProgress() {
        try {
            BatchStatusDto savedStatus = batchFileService.readJsonFile(BATCH_FILE_NAME);
            
            if (savedStatus != null && !savedStatus.isCompleted()) {
            	log.warn("[배치 복구] 기존 {} 파일 복구중..", BATCH_FILE_NAME);
                
                // 1. 서버가 다운되어 있는 동안 흘러간 시간을 반영하여 진짜 남은 시간(초) 계산
                long now = System.currentTimeMillis();
                long totalLimitSeconds = (long) savedStatus.getTotalMinutes() * 60; // 최초 설정된 총 제한 시간(초)
                long realElapsedSeconds = (now - savedStatus.getStartTimeMillis()) / 1000; // 실제 처음 시작 후 흐른 시간
                long realRemainingSeconds = totalLimitSeconds - realElapsedSeconds; // 진짜 남은 시간

                log.warn("======================================================================");
                log.warn("[배치 시간 보정 복구] 기존 {}스텝 진행 중 정전 발생 감지.", savedStatus.getCurrentStep());
                log.warn("[시간 모니터링] 최초 제한: {}초 | 가동 후 총 경과시간: {}초 | 다운타임 반영 실제 남은시간: {}초", 
                        totalLimitSeconds, realElapsedSeconds, realRemainingSeconds);
                log.warn("======================================================================");

                // 마감 시간이 이미 지났는데 배치가 끝나지 않은 경우 예외 처리
                if (realRemainingSeconds <= 0) {
                    log.error("=== [복구 실패] 서버가 다운된 사이 마감 제한 시간이 초과되었습니다. 배치를 재개할 수 없습니다. ===");
                    //savedStatus.setCompleted(true);
                    batchFileService.createJsonFile(BATCH_FILE_NAME, savedStatus);
                    return;
                }

                // 2. 상태값 복원
                currentStep.set(savedStatus.getCurrentStep() + 1);
                realFailCount.set(savedStatus.getRealFailCount());    // 기존 실패 횟수 그대로 이어받기
                batchStartTime.set(savedStatus.getStartTimeMillis()); // 기존 시작 시간 유지

                // 3. '진짜 남은 시간'과 '남은 스텝 횟수'를 기반으로 압축된 새 주기를 계산하여 가동
                int totalExecuteCount = savedStatus.getTotalExecuteCount();
                int remainingSteps = totalExecuteCount - savedStatus.getCurrentStep(); // 남은 실행 횟수
                
                if (remainingSteps > 0) {
                    long compressedPeriodSeconds = realRemainingSeconds / remainingSteps;
                    if (compressedPeriodSeconds < 10) compressedPeriodSeconds = 10; // 최소 간격 방어
                    
                    log.info("[주기 압축 완료] 남은 수량 {}회를 {}초 안에 채우기 위해 새 주기를 {}초 간격으로 가속합니다.", 
                            remainingSteps, realRemainingSeconds, compressedPeriodSeconds);
                    
                    String strategyFileName = savedStatus.getFileName();
                    strategyFile = strategyFileName;
                    startSchedulerEngineWithPeriod(strategyFileName,totalExecuteCount, savedStatus.getTotalMinutes(), savedStatus.getOrderTotalAmount() , compressedPeriodSeconds,retry.get());
                }
            }else {
            	log.warn("[배치 ] 기존 {} 미완료 배치 없음", BATCH_FILE_NAME);
            }
        } catch (IOException e) {
            log.error("배치 복구 로드 중 예외 발생: ", e);
        }
    }
    

    /**
     * 실시간 지나간 시간 / 남은 시간을 파싱하여 파일에 쓰는 핵심 헬퍼 메서드
     */
    private void saveBatchStatusToDisk(String strategyFileName, int step, int totalExecuteCount, int totalMinutes,int orderTotalAmount, boolean isCompleted, long currentPeriod) {
        try {
            BatchStatusDto statusDto = batchFileService.initJsonFile(BATCH_FILE_NAME);
            
            long now = System.currentTimeMillis();
            long start = batchStartTime.get();
            long totalLimitSeconds = (long) totalMinutes * 60; // 전체 제한 시간(초)
            
            // 실시간 시간 계산 (초 단위)
            long elapsedSeconds = (now - start) / 1000;
            long remainingSeconds = totalLimitSeconds - elapsedSeconds;
            if (remainingSeconds < 0) remainingSeconds = 0; // 음수 방지

            // DTO 데이터 바인딩
            statusDto.setCurrentStep(step);
            statusDto.setTotalExecuteCount(totalExecuteCount);
            statusDto.setTotalMinutes(totalMinutes); // 가독성을 위해 기존 필드명에 분(Minutes) 주입 유지
            statusDto.setCompleted(isCompleted);
            statusDto.setOrderTotalAmount(orderTotalAmount); //주문 총량
            statusDto.setOrderRemainedAmount(orderRemainedAmountChanged.get());
            
            
            // 신규 시간 데이터 주입
            statusDto.setStartTimeMillis(start);
            statusDto.setElapsedSeconds(elapsedSeconds);
            statusDto.setRemainingSeconds(remainingSeconds);
            
            //전역 변수에서 누적된 실패 카운트를 가져와 DTO에 실시간 동기화
            statusDto.setRealFailCount(realFailCount.get());
            
            // 매매전략 파일의 이름을 저장
            statusDto.setFileName(strategyFileName);
            
            // 디스크 및 메모리 멀티 캐시 동시 저장
            batchFileService.createJsonFile(BATCH_FILE_NAME, statusDto);

            log.info("[디스크 백업 기록] 스텝: {}/{} | 누적 실패: {}회 | 진행시간: {}초 | 남은시간: {}초", 
                    step, totalExecuteCount, statusDto.getRealFailCount(), elapsedSeconds, remainingSeconds);
            

            if(isCompleted) {
            	log.info("=최종완료 되었으면 이제 이걸로 최종 보고서 메일 발송 ");
            }
            
            
        } catch (IOException e) {
            log.error("배치 타임스탬프 진행 상황 디스크 백업 실패!", e);
        }
    }

    
    
    /**
     * 공통 스케줄러 엔진 구동 (계산된 고정 주기를 주입받아 작동)
     */
    private void startSchedulerEngineWithPeriod(String strategyFileName, int totalExecuteCount, int totalMinutes, int orderTotalAmount, long periodInSeconds,int retry) {
        log.info("[배치 가동] 인터벌 {}초 간격으로 주행을 시작합니다. (목표 횟수: {}회, 현재 스텝: {})", 
                periodInSeconds, totalExecuteCount, currentStep.get());
        
        ScheduledFuture<?> scheduledFuture = taskScheduler.scheduleAtFixedRate(() -> {
            int step = currentStep.get(); 

            if (step <= totalExecuteCount) {
                // 1. 비즈니스 로직 수행
            	ProcessResult a = executeWithRetry(step,retry,totalExecuteCount,orderTotalAmount, orderRemainedAmountChanged.get());
            	orderRemainedAmountChanged.set(a.orderRemainedAmount());
            	//log.info("@@@@@@@@@@@@@@@@@ {}: a.orderRemainedAmount()" ,a.orderRemainedAmount());
            	
                // 2. 마지막 단계 여부 확인
                boolean isLastStep = (step == totalExecuteCount);
                
                // 3. 실시간 시간 연산 후 파일 및 메모리 백업
                saveBatchStatusToDisk(strategyFileName,step, totalExecuteCount, totalMinutes ,orderTotalAmount, isLastStep, periodInSeconds);

                // 4. 안전하게 다음 스텝으로 카운트 증가
                currentStep.incrementAndGet();
            } else {
                // 목표 타겟 완수 시 해제
                ScheduledFuture<?> future = futureRef.get();
                if (future != null) {
                    future.cancel(false);
                    log.info("=== [배치 종료] 모든 고정 주행 스텝을 정상 완료했습니다. ===");
                }
            }
        }, Duration.ofSeconds(periodInSeconds));

        futureRef.set(scheduledFuture);
    }
    
    

    /**
     * 최초 정각 정상 동적 스케줄링 실행 진입점
     * @param totalExecuteCount 목표 성공 횟수 (예: 100회)
     * @param totalHours  제한 시간 (예: 300분)
     */
    public void runOrderBatchTask(String strategyFileName,int totalExecuteCount, int totalMinutes, int orderTotalAmount,int tradeSignal, int retryCnt,int test1or0) {
        
    	retry.set(retryCnt);
    	test.set(test1or0);
    	buyOrSell.set(tradeSignal);   
    	currentStep.set(1);        
        realFailCount.set(0);      
        batchStartTime.set(System.currentTimeMillis()); // 최초 시작 타임스탬프 발행
        orderRemainedAmountChanged.set(orderTotalAmount);
        
        
        //totalExecuteCount =300; //고정 
        //totalMinutes =1분동안 ,300분
        //orderTotalAmount = 700; // 700
        if(orderTotalAmount<=10) { //10개 이하면 10분만에 실행
        	totalMinutes = orderTotalAmount;
        }
        
        if(orderTotalAmount<=totalExecuteCount) { // <=300
        	//orderTotalAmount 갯수만큼 실행횟수
        	totalExecuteCount = orderTotalAmount;
        }else {
        	//300 초과시에는 분할수행
        }
        
        long totalSeconds = (long) totalMinutes * 60;
        long defaultPeriodSeconds = totalSeconds / totalExecuteCount; // 기본 정상 주기 계산 (예: 300분/300회 = 1분)

        // 최초 0스텝 기본 뼈대 백업 생성
        saveBatchStatusToDisk(strategyFileName,0, totalExecuteCount, totalMinutes, orderTotalAmount, false, defaultPeriodSeconds);
        
        // 스케줄러 엔진 구동
        startSchedulerEngineWithPeriod(strategyFileName, totalExecuteCount, totalMinutes, orderTotalAmount, defaultPeriodSeconds,retry.get());
        strategyFile = strategyFileName;
    }
    
	/*
	 * 로직을 실행하고, 실패 시 최대 maxRetries번 재시도하는 메서드
	 */
    private ProcessResult executeWithRetry(int step, int maxRetries, int totalExecuteCount, int orderTotalAmount, int orderRemainedAmount) {
        //int maxRetries = 1; 
        int retryIntervalMillis = 5000; 

        for (int retry = 0; retry <= maxRetries; retry++) {
            if (retry > 0) {
                log.info("   -> [재시도 {}/{}] {}번째 작업 다시 시도 중...", retry, maxRetries, step);
            } else {
                log.info("[{} / {}회차 작업 시작]", step, currentStep.get());  
            }

            //boolean isSuccess = processBusinessLogic(step,totalExecuteCount,orderTotalAmount);

            ProcessResult result =processBusinessLogic(step,totalExecuteCount,orderTotalAmount,orderRemainedAmount);

            boolean isSuccess = result.isSuccess();
            if (isSuccess) {
                log.info(" -> [{}번째 작업 성공]", step);
                return result; // 성공했으므로 재시도 루프 탈출
            }

            // 실패했고, 아직 재시도 기회가 남았다면 대기 후 다시 돌림
            if (retry < maxRetries) {
                try {
                    Thread.sleep(retryIntervalMillis); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return result;
                }
            } else {
                realFailCount.incrementAndGet();
                log.warn(" -> [각작업당 최종 실패] {}번째 작업은 반복 재시도 후에도 통과하지 못했습니다.", step);
                return result;
            }
        }
		return new ProcessResult(false,orderRemainedAmount);
    }
    
    
    /**
     * 결과값을 반환하도록 수정한 핵심 비즈니스 로직
     * @return 통과 여부 (true = 성공, false = 실패)
     */
    private ProcessResult processBusinessLogic(int  step,int totalExecuteCount,int orderTotalAmount,int orderRemainedAmount) {

        int amountPerOrder = ((orderRemainedAmount-1) / (totalExecuteCount-step+1) ) +1;
        log.info("[작업 시작] " + step + "번째 실행 시도 중...{}-총횟수, {}-총주문갯수,, {}-남은주문갯수, {}-주문당주문갯수",totalExecuteCount,orderTotalAmount,orderRemainedAmount,amountPerOrder);  //  \n
        
        try {
        	boolean orderExcuted= false;
        	
        	// -------------------------------------------------------------
            // 여기에 실제 실행할 비즈니스 로직을 작성합니다.
            // 예시: 외부 API 호출, DB 저장 등
            // -------------------------------------------------------------
            
        	//JsonNode jsonNode = PriceService.getPrice("TQQQ", "NAS");  ////NYS : 뉴욕 NAS : 나스닥 AMS : 아멕스
        	//String firstOutput = jsonNode.get("output").asText();
        	
        	//String firstOutput = jsonNode.get("output").asText();
        	//log.info(firstOutput);
        	
        	/*
        	StockInfoDto data = infoJsonFileService.initJsonFile(strategyFile);
        	String ticker = data.getTicker();
			String tradeOffice =data.getTradeOffice();
			double averageSignalPrice = data.getAverageSignalPrice();
			
			if(tradeOffice.equals("NASD"))tradeOffice ="NAS";
			if(tradeOffice.equals("AMEX"))tradeOffice ="AMS";
			if(tradeOffice.equals("NYSE"))tradeOffice ="NYS";
			log.info("{} strategyFile : {}",ticker,strategyFile);
        	Double nowPrice = stockAnalysisService.getPrice(strategyFile,ticker, tradeOffice);   //  TQQQ TQQQ "NAS"
        	log.info("{} 조회가격 : {}",ticker,nowPrice);  //75.34
        	
        	boolean test = true;  // true  false
        	if(test) {
        		log.warn("테스트중: {} 조회가격 : {} 이 이평선 {} 과 {}%  차이남",ticker,nowPrice,averageSignalPrice,gapPercent);  //75.34
        		nowPrice=averageSignalPrice*1;
        	}
        	
        	String nowPriceStr = BigDecimal.valueOf(nowPrice).setScale(2, RoundingMode.DOWN).toString();
        	if(averageSignalPrice*(1.07) > nowPrice) {
        		log.warn("{} 조회가격 : {} 이 이평선 {} 과 {}%  차이남",ticker,nowPriceStr,averageSignalPrice,gapPercent);  //75.34
        		orderExcuted= stockOrderService.executeOrderLogic(nowPriceStr,data,perOrder,buyOrSell );
        	}else {
        		log.warn("{} 조회가격 : {} 이 이평선 {} 과 {}% 이상 차이남",ticker,nowPriceStr,averageSignalPrice,gapPercent);  //75.34
            	orderExcuted =false;
        	}
        	
        	*/

            // 검증 로직 결과에 따라 true 또는 false 반환
            boolean validationResult = checkYourCondition(); 
            
            //String buyOrSell ="BUY"; //SELL
            // 작성된 파일 내용을 바탕으로 매매 실행 함수
        	orderExcuted = stockOrderService.buyOrSellExecutions(strategyFile,buyOrSell.get() , amountPerOrder,test.get());
            if(orderExcuted) {
            	orderRemainedAmount =orderRemainedAmount-amountPerOrder;
            }
            
            //return orderExcuted; 
            return new ProcessResult(orderExcuted,orderRemainedAmount);
            
        } catch (Exception e) {
            // 에러 발생 시 실패(false) 처리하여 카운트 제외
            log.error("로직 실행 중 예외 발생:{} " , e.getMessage());
            return new ProcessResult(false,orderRemainedAmount);
        }
    }

    // 예시용 검증 메서드 (실제 프로젝트의 검증 조건으로 대체하세요)
    private boolean checkYourCondition() {
    	
    	
    	
    	
        // 무조건 통과한다고 가정 (실패 테스트 시 false 반환하도록 제어 가능)
    	//return false;
    	return true; 
    }
}
