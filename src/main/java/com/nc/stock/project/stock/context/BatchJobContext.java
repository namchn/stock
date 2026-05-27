package com.nc.stock.project.stock.context;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import lombok.Getter;
import lombok.Setter;



@Getter
//@Setter  // 외부에서 임의로 변경하는 것을 막기위해 내부 메소드 추가로 대응
public class BatchJobContext {

	
    private final String strategyFileName;
    private final String batchFileName; // order_batch_file_날짜_전략명
    
    // 각 스케줄러마다 독립적으로 유지되어야 하는 상태들
    private final AtomicInteger currentStep = new AtomicInteger(1);
    private final AtomicInteger realFailCount = new AtomicInteger(0);
    private final AtomicLong batchStartTime = new AtomicLong(0);
    private final AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

    //private final AtomicInteger maxRetries = new AtomicInteger(0);
    
    // 변수를 context로 옮김
    private final AtomicInteger orderRemainedAmountChanged = new AtomicInteger(1);
    //private final AtomicInteger orderRemainedAmount = new AtomicInteger(0);
    
    private final AtomicInteger retry = new AtomicInteger(1); //반복횟수
    private final AtomicInteger test = new AtomicInteger(0); 
    private final AtomicInteger buyOrSell = new AtomicInteger(1);
    
    private final AtomicInteger totalMinutes = new AtomicInteger(1);
    private final AtomicInteger totalExecuteCount = new AtomicInteger(1);
    private final AtomicInteger orderTotalAmount = new AtomicInteger(1);
    
    private final AtomicLong defaultPeriodSeconds = new AtomicLong(10);
    private final AtomicBoolean isCompleted = new AtomicBoolean(false);
    
    
    
    
    public BatchJobContext(String strategyFileName, String todayStr) {
        this.strategyFileName = strategyFileName;
        // 핵심: 전략 파일명을 붙여서 파일이 서로 덮어쓰지 않도록 방어
        this.batchFileName = "order_batch_file_" + strategyFileName.replace(".", "_")+ todayStr ; // + "_"
    }
    
    public BatchJobContext(String strategyFileName, String todayStr, int buyOrSell, int test) {
        this.strategyFileName = strategyFileName;
        this.batchFileName = "order_batch_file_" + strategyFileName.replace(".", "_")+ todayStr ; // + "_"
        //this.buyOrSell = buyOrSell;  // this.buyOrSell.set(buyOrSell);
        //this.test = test;            // this.test.set(test);
    }

    
    // BatchJobContext 내부 추가 생성자
    public BatchJobContext(String strategyFileName
    						, String todayStr
    						, int retry
    						, int test
    						, int buyOrSell
    						, int totalMinutes
    						, int totalExecuteCount
    						, int orderTotalAmount
    						
    						) {
        this.strategyFileName = strategyFileName;
        this.batchFileName = "order_batch_file_" + strategyFileName.replace(".", "_") + "_" + todayStr;
        this.isCompleted.set(false); 
        
        this.retry.set(retry);
        this.test.set(test);
        this.buyOrSell.set(buyOrSell);
        
        this.currentStep.set(1);   
        this.realFailCount.set(0);
        this.batchStartTime.set(System.currentTimeMillis());
    	
        this.totalMinutes.set(totalMinutes);
        this.totalExecuteCount.set(totalExecuteCount);
        this.orderTotalAmount.set(orderTotalAmount);
        this.orderRemainedAmountChanged.set(orderTotalAmount); // 초기 잔여량 = 전체량
        
    }
    
    
    
    
    
    // Getter들 (생략)
    public String getStrategyFileName() { return strategyFileName; }
    public String getBatchFileName() { return batchFileName; }
    public AtomicInteger getCurrentStep() { return currentStep; }
    public AtomicInteger getRealFailCount() { return realFailCount; }
    public AtomicLong getBatchStartTime() { return batchStartTime; }
    public AtomicInteger getOrderRemainedAmountChanged() { return orderRemainedAmountChanged; }
    public AtomicReference<ScheduledFuture<?>> getFutureRef() { return futureRef; }



    // 내부 상태 변경은 객체 스스로가 수행 (캡슐화)
    public int currentStepIncrementAndGet() { return this.currentStep.incrementAndGet(); }
    public int realFailCountIncrementAndGet() { return this.realFailCount.incrementAndGet(); }
    
    //남은 수량 차감과 디스크 저장을 동시성 위험 없이
    public synchronized  void orderRemainedAmountChangedMinus(int amount) { this.orderRemainedAmountChanged.addAndGet(-amount); }
    
    public boolean isJobCompleted() { return this.isCompleted.get(); }
    
    public int getRemainingSteps() {
        return this.totalExecuteCount.get() - this.currentStep.get() + 1;
    }
    
    //주문당 수량 계산 시 데이터 정합성이 깨지지 않도록 동기화
    public synchronized  int calculateAmountPerOrder() {
        int remainedAmount = this.orderRemainedAmountChanged.get();
        int remainingSteps = getRemainingSteps();
        if (remainingSteps <= 0) return remainedAmount;
        return ((remainedAmount - 1) / remainingSteps) + 1;
        // ((orderRemainedAmountChanged-1) / (totalExecuteCount-currentStep+1) ) +1
    }

    public boolean isCircuitBreakerTriggered() {
        int total = this.totalExecuteCount.get();
        return total > 10 && this.realFailCount.get() > (total / 2);
    }
    
    
    /**
     * 원본 데이터를 훼손하지 않고, 지연 상황을 고려한 다음 실행 절대 시각(Instant)을  계산합니다.
     * @return 다음 스케줄러 예약에 사용할 Instant 객체
     */
    public java.time.Instant calculateNextExecutionInstant() {
        int step = this.currentStep.get();
        long startTime = this.batchStartTime.get();
        long periodInSeconds = this.defaultPeriodSeconds.get();
        
        //최초 가동 시점 기준 이론상 정상적인 다음 실행 시각 계산 (startTime + 현재완료스텝 * 주기)
        long nextExecutionMillis = startTime + (step * periodInSeconds * 1000);
        Instant nextTargetInstant = Instant.ofEpochMilli(nextExecutionMillis);
        
        // 만약 시스템 지연으로 인해 계산된 다음 시간이 '이미 지난 과거'라면 안전하게 (now)+periodInSeconds 실행되도록 방어
        if (nextTargetInstant.isBefore(Instant.now())) {
            // 원본 멤버 변수는 건드리지 않고, 다음 예약 시점만 '현재 시간 + 고정 주기'로 안전 점프
            return Instant.now().plusSeconds(periodInSeconds);
        }
        
        return nextTargetInstant;
    }
}