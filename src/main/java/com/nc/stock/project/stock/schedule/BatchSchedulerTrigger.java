package com.nc.stock.project.stock.schedule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nc.stock.project.stock.context.MultiBatchOrderService;
import com.nc.stock.project.stock.dto.GlobalStrategyConfigDto;
import com.nc.stock.project.stock.facade.StockAnalysisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchSchedulerTrigger {

    private final StockAnalysisService stockAnalysisService;
    private final MultiBatchOrderService multiBatchOrderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 설정 파일 절대 경로 정의
    private final String CONFIG_FILE_PATH = "config/strategy/global_strategy_config.json";

    /**
     * 주중 오후 11시 30분에 배치 프로세스 시작 (JSON 설정 기반 동적 구동)
     */
    //@Scheduled(cron = "10 45 23 * * MON-FRI", zone = "Asia/Seoul")
    //@Scheduled(cron = "10 52 17 * * MON-FRI", zone = "Asia/Seoul")
    public void startBatch() {
        log.info("[스케줄러 트리거] JSON 파일로부터 배치 메타데이터 로드를 시도합니다.");
        
        // 1. 실시간 JSON 설정 파일 로드 (서버 재기동 불필요)
        GlobalStrategyConfigDto config;
        try {
            File configFile = Paths.get(CONFIG_FILE_PATH).toFile();
            if (!configFile.exists()) {
                log.error("[트리거 중단] 글로벌 배치 설정 JSON 파일이 지정된 경로에 존재하지 않습니다: {}", CONFIG_FILE_PATH);
                return;
            }
            config = objectMapper.readValue(configFile, GlobalStrategyConfigDto.class);
        } catch (IOException e) {
            log.error("[트리거 중단] 설정 JSON 파일 파싱 중 치명적 오류 발생", e);
            return;
        }

        log.info("[스케줄러 트리거] 설정 로드 완료. 가동 대상 종목 수: {}개", config.getTargets().size());
        int test1or0 = config.isTestMode() ? 1 : 0;

        // 2. JSON에 등록된 모든 종목 루프 구동
        for (GlobalStrategyConfigDto.TargetConfig target : config.getTargets()) {
            String strategyFileName = target.getStrategyFileName();
            String ticker = target.getTicker();

            try {
                // 3. 매매 시그널 판단
                int tradeSignal = config.isTestMode() ? 2 : stockAnalysisService.confirmOrderSignal(strategyFileName, ticker);
                
                int orderTotalAmount = 0;
                String signalType = "";

                if (tradeSignal == 1) {
                    signalType = "매수";
                    orderTotalAmount = stockAnalysisService.getOrderStock(strategyFileName, ticker);
                } else if (tradeSignal == 2) {
                    signalType = "매도";
                    orderTotalAmount = stockAnalysisService.getPositionStock(strategyFileName, ticker);
                } else {
                    log.info("[시그널 관망] {} ({}) 종목은 진입 조건이 아닙니다.", strategyFileName, ticker);
                    continue;
                }

                // 4. 수량 유효성 검증 후 안전 격리된 동적 멀티 배치 엔진 호출
                if (orderTotalAmount > 0) {
                    log.warn("[시그널 감지] {} 종목 대량 {} 시그널 확정! 목표 주문량: {}개", strategyFileName, signalType, orderTotalAmount);
                    
                    multiBatchOrderService.runOrderBatchTask(
                        strategyFileName, 
                        config.getTotalExecuteCount(), 
                        config.getTotalMinutes(), 
                        orderTotalAmount, 
                        tradeSignal, 
                        1, 
                        test1or0
                    );
                } else {
                    log.info("[가동 패스] {} ({}) 종목의 {} 가능 수량이 0개이므로 배치를 생성하지 않습니다.", strategyFileName, ticker, signalType);
                }

            } catch (Exception e) {
                // 장애 격리: 특정 종목의 에러가 다른 종목 배치를 셧다운 시키지 않도록 방어
                log.error("[종목 가동 에러] {} ({}) 처리 중 예외 발생. 다음 타겟으로 이동합니다.", strategyFileName, ticker, e);
            }
        }
    }
}
