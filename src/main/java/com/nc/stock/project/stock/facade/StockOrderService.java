package com.nc.stock.project.stock.facade;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nc.stock.project.stock.dto.StockInfoDto;
import com.nc.stock.project.stock.json.InfoJsonFileService;
import com.nc.stock.project.stock.service.OrderService;
import com.nc.stock.project.stock.service.PriceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockOrderService {

	private final InfoJsonFileService infoJsonFileService;
    private final OrderService orderService;
    private final PriceService priceService;
    private final StockAnalysisService stockAnalysisService; 
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * json 에 적재
     */
    public void saveJson(String fileName, String toPrice, String prePrice,Double movingAverage200) {
    	StockInfoDto data;
		try {
			data = infoJsonFileService.initJsonFile(fileName);
			
			//가공
			Double toPriceDouble = Double.parseDouble(toPrice);
			Double prePriceDouble = Double.parseDouble(prePrice);
			Double maxSignalPrice = data.getMaxSignalPrice()==null?0.01:data.getMaxSignalPrice();
			maxSignalPrice = Math.max(maxSignalPrice, toPriceDouble); 
			String nowSignal = (toPriceDouble>=movingAverage200)?"H":"S";
			String preSignal = (prePriceDouble>=movingAverage200)?"H":"S";
			
			//주입
			data.setAverageSignalPrice(movingAverage200);
			data.setMaxSignalPrice(maxSignalPrice);
			data.setNowSignal(nowSignal);
			data.setPreSignal(preSignal);
			
			infoJsonFileService.updateJsonFile(fileName, data);
		} catch (IOException e) {
			log.error(e.getMessage());
		}
    }
    
    
    /*
    public JsonNode call(String fileName,String ticker, String market,int minus) {
        try {
        	JsonNode firstResponse = priceService.getDailyPrice2(ticker, market, todayStr, 0);
        } catch (Exception e) {
			log.error("[{}] 200일 이동평균선 계산 중 예외 발생: {}", ticker, e.getMessage(), e);
			return null;
		}
     }
    */
    
    public boolean  buyOrSellExecutions(String strategyFile,int buyOrSell,int amountPerOrder,int test1or0) {
    	try {
    		boolean orderExcuted= false;
    		
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

        	// 매수시작
        	// 매수 신호를 확인하고 현재값과  7% 이상 차이나지 않을때 매수 
        	// totalExecuteCount 가 100 이상이면 주문당 갯수 늘림
        	String perOrder =  (amountPerOrder>1)?String.valueOf(amountPerOrder):"1";
        	Double gapPercent = 100*(nowPrice  - averageSignalPrice)/averageSignalPrice;
        	

        	//boolean test = true;  // true  false
        	if(test1or0==1) {
        		//buyOrSell ="BUY"; "BUY" =1 "SELL"=2
        		//buyOrSell =2;
        		log.warn("테스트중 {}: {} 조회가격 : {} 이 이평선 {} 과 {}%  차이남",buyOrSell,ticker,nowPrice,averageSignalPrice,gapPercent);  //75.34
        		if(buyOrSell==1)nowPrice=nowPrice*0.5;
        		if(buyOrSell==2)nowPrice=nowPrice*1.5;
        	}
        	
        	String nowPriceStr = BigDecimal.valueOf(nowPrice).setScale(2, RoundingMode.DOWN).toString();
        	
        	if(buyOrSell==1) {
        		if(averageSignalPrice*(1.07) > nowPrice) {
            		log.warn("매수시: {} 매수주문조회가격 : {} 이 이평선 {} 과 {}%  차이남",ticker,nowPriceStr,averageSignalPrice,gapPercent);  //75.34
            		orderExcuted= executeOrderLogic(nowPriceStr,data,perOrder,"BUY" );
            	}else {
            		log.warn("매수시: {} 매수주문조회가격 : {} 이 이평선 {} 과 {}% 이상 차이남",ticker,nowPriceStr,averageSignalPrice,gapPercent);  //75.34
            		//orderExcuted= executeOrderLogic(nowPriceStr,data,perOrder,"BUY" );
            		orderExcuted =false;
            	}
        	}
        	
        	if(buyOrSell==2) {
        		log.warn("매도시: {} 매도주문가격 : {} 이 이평선 {} 과 {}% 이상 차이남",ticker,nowPriceStr,averageSignalPrice,gapPercent);  //75.34
        		orderExcuted= executeOrderLogic(nowPriceStr,data,perOrder,"SELL" );
        	}
        	
        	
        	return orderExcuted;
    	} catch (Exception e) {
            // 에러 발생 시 실패(false) 처리하여 카운트 제외
            log.error("로직 실행 중 예외 발생:{} " , e.getMessage());
            return false;
        }
    }
    
    
    
    /**
     * 특정 종목의 매수 주문을 합니다
     */
    public boolean executeOrderLogic(String nowPrice, StockInfoDto data,String perOrder,String buyOrSell ) {
    	//NASD : 나스닥 NYSE : 뉴욕 AMEX : 아멕스
    	
    	String ticker = data.getTicker();
    	String office = data.getTradeOffice();
    	JsonNode response = orderService.postOrder(ticker,office,nowPrice,perOrder,"00",buyOrSell);  // TQQQ "NASD"
    	//JsonNode response = orderService.postOrder("SPYM","AMEX","86.26","1","00","BUY");  // SPYM "AMEX"
        
    	/*
    	  String ticker
		, String office
		, String orderPrice
		, String num
		, String orderType
		, String buy) {
    	*/
			
    	if(isResponseValid( response)) {
        	log.info("=executeOrderLogic!!!!!!!!!!!!!!!!!!!!!!!=");
        	return true;
    	}

    	return false;
    	
    }
    
    

	private boolean isResponseValid(JsonNode response) {
		if (response == null || !"0".equals(response.path("rt_cd").asText())) {
			log.warn("API 정상 응답이 아닙니다. 응답 코드: {}", response != null ? response.path("msg1").asText() : "null");
			return false;
		}
		return true;
		//JsonNode output = response.get("output2");
		//return output != null && output.isArray() && !output.isEmpty();
	}

}
