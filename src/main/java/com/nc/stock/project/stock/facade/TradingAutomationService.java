package com.nc.stock.project.stock.facade;

import java.util.stream.StreamSupport;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nc.stock.project.stock.dto.StockInfoDto;
import com.nc.stock.project.stock.json.InfoJsonFileService;
import com.nc.stock.project.stock.service.ForeignMarginOverseasService;
import com.nc.stock.project.stock.service.OrderService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingAutomationService {

    private final ForeignMarginOverseasService marginService;
    private final InfoJsonFileService jsonFileService;
    //private final IndicatorService indicatorService; // 이동평균선 등 지표 계산 서비스 (가정)
    private final OrderService orderService;         // 매수/매도 주문 서비스 (가정)

    
    
    
    
    
    
    
    
    
    /*
    @Transactional
    public void executeAutoTradingWorkflow() {
        // 1. 해외 증거금 및 주문 가능 금액 조회
        Double usableCash = fetchUsableCash();
        if (usableCash == null) return;

        // 2. 로컬 설정(StockData) 갱신
        updateStockData(usableCash);

        // 3. 매매 신호 확인 (예: 200일선 위인지 판단)
        if (indicatorService.isAbove200DayMovingAverage("USA_STOCK")) {
            log.info("200일선 위 신호 포착 - 매수 로직을 실행합니다.");
            orderService.buyProcess(usableCash);
        } else {
            log.info("매수 조건 미충족 또는 매도 신호 확인");
            // 필요 시 매도 로직 실행
        }
    }
	*/
    
    /*
    private Double fetchUsableCash() {
        JsonNode jsonNode = marginService.getBalanceM();
        if (jsonNode == null || !"0".equals(jsonNode.path("rt_cd").asText())) {
            log.warn("증거금 API 호출 실패 또는 정상 응답이 아닙니다.");
            return null;
        }

        JsonNode outputArray = jsonNode.get("output");
        if (outputArray == null || !outputArray.isArray()) return null;

        return StreamSupport.stream(outputArray.spliterator(), false)
                .filter(node -> "미국".equals(node.path("natn_name").asText()))
                .findFirst()
                .map(node -> node.path("frcr_gnrl_ord_psbl_amt").asDouble())
                .orElse(null);
    }
    */

    /*
    private void updateStockData(double usableCash) {
        StockInfoDto data = jsonFileService.initJsonFile("StockData");
        data.setUsableCash(usableCash);
        jsonFileService.updateJsonFile("StockData", data);
    }
    */
}