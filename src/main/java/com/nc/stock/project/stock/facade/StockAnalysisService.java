package com.nc.stock.project.stock.facade;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nc.stock.project.stock.dto.StockInfoDto;
import com.nc.stock.project.stock.json.InfoJsonFileService;
import com.nc.stock.project.stock.service.PriceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockAnalysisService {

	
    private final InfoJsonFileService infoJsonFileService;
    private final PriceService priceService;
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
			Double minSignalPrice = data.getMinSignalPrice()==null?10000.01:data.getMinSignalPrice();
			minSignalPrice = Math.min(minSignalPrice, toPriceDouble); 
			String nowSignal = (toPriceDouble>=movingAverage200)?"H":"S";
			String preSignal = (prePriceDouble>=movingAverage200)?"H":"S";
			
			//주입
			data.setAverageSignalPrice(movingAverage200);
			data.setMaxSignalPrice(maxSignalPrice);
			data.setMinSignalPrice(minSignalPrice);
			data.setNowSignal(nowSignal);
			data.setPreSignal(preSignal);
			data.setNowPrice(toPriceDouble); //갯수 계산용
			
			infoJsonFileService.updateJsonFile(fileName, data);
		} catch (IOException e) {
			log.error(e.getMessage());
		}
    }
    public void saveJson2(String fileName,String ticker, String nowPrice) {
    	StockInfoDto data;
		try {
			data = infoJsonFileService.initJsonFile(fileName);
			
			//가공
			Double nowPriceDouble = Double.parseDouble(nowPrice);
			
			//주입
			data.setNowPrice(nowPriceDouble);
			
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
    
    
    /*
    public Double  aa(String fileName,String ticker, String market,int minus) {
    	String todayStr = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(minus).format(DATE_FORMATTER);

    	try {
    		JsonNode firstResponse = priceService.getDailyPrice2(ticker, market, todayStr, 0); //"TQQQ","NAS"
            if (!isResponseValid(firstResponse)) return null;
            JsonNode firstOutput = firstResponse.get("output2");
            String toPrice = firstOutput.get(0).get("clos").asText();
            Double toPriceDouble = Double.parseDouble(toPrice);
            return toPriceDouble;
            
		} catch (Exception e) {
			log.error("[{}] 200일 이동평균선 계산 중 예외 발생: {}", "ticker", e.getMessage(), e);
			return null;
		}
		
    	
    }
    */
    
    public Double  getPrice(String fileName,String ticker, String market) {
    	//String todayStr = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(minus).format(DATE_FORMATTER);
		try {
			StockInfoDto data = infoJsonFileService.initJsonFile(fileName);
			ticker = data.getTicker();
			JsonNode jsonNode = priceService.getPrice(ticker, market); //// NYS : 뉴욕 NAS : 나스닥 AMS : 아멕스
			if (!isResponseValidBasic(jsonNode))
				return null;
			String last = jsonNode.get("output").get("last").asText();
			Double lastPriceDouble = Double.parseDouble(last);
			
			saveJson2(fileName,ticker,last);
			return lastPriceDouble;

		} catch (Exception e) {
			log.error("[{}] getPrice 예외 발생: {}", fileName, e.getMessage(), e);
			return null;
		}
    }
    
    /**
     * 매수 가능 갯수를 가져 옵니다.
     */
    public int  getOrderStock(String fileName,String ticker) {
    	StockInfoDto data;
		try {
			data = infoJsonFileService.initJsonFile(fileName);
	    	return Integer.parseInt(data.getOrderStock());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			log.error("[{}] getOrderStock 예외 발생: {}", fileName, e.getMessage(), e);
			return 0;
		}
    }
    
    /**
     * 매더 가능 갯수를 가져 옵니다.
     */
    public int  getPositionStock(String fileName,String ticker) {
    	StockInfoDto data;
		try {
			data = infoJsonFileService.initJsonFile(fileName);
	    	return Integer.parseInt(data.getPositionStock());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			log.error("[{}] getOrderStock 예외 발생: {}", fileName, e.getMessage(), e);
			return 0;
		}
    }
    
    /**
     * 매수,매도 시그널 확인합니다.
     */
    public int  confirmOrderSignal(String fileName,String ticker) {
    	int tradeSignal =0;  //1 이면 매수 2면 매도 
    	StockInfoDto data;
		try {
			data = infoJsonFileService.initJsonFile(fileName);
			String preSignal = data.getPreSignal();
			String nowSignal = data.getNowSignal();
			
			if(preSignal.equalsIgnoreCase("S")&&nowSignal.equalsIgnoreCase("H") ) {
				tradeSignal =1; //매수신호
			}
			if(preSignal.equalsIgnoreCase("H")&&nowSignal.equalsIgnoreCase("S") ) {
				tradeSignal =2; //매도신호
			}
	    	return tradeSignal;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			log.error("[{}] getOrderStock 예외 발생: {}", fileName, e.getMessage(), e);
			return tradeSignal;
		}
    }
    
    
    
    /**
     * 특정 종목의 200일 이동평균 가격을 계산합니다.
     */
    public Double calculate200DayMovingAverage(String fileName,String ticker, String market,int minus) {
        try {
            String todayStr = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(minus).format(DATE_FORMATTER);

            // 1. 첫 번째 API 호출 (최근 100일 데이터)
            JsonNode firstResponse = priceService.getDailyPrice2(ticker, market, todayStr, 0); //"TQQQ","NAS"
            if (!isResponseValid(firstResponse)) return null;
            //String rsym =firstResponse.get("output1").get("rsym").asText(); //simbol
            
            JsonNode firstOutput = firstResponse.get("output2");
            
            double firstTotal = calculateTotalClose(firstOutput);
            int firstSize = firstOutput.size();

			// 100일치가 다 안 오면 200일선 계산 불가능
			if (firstSize < 100) {
				log.warn("{}의 최근 데이터가 부족합니다. (수신 데이터: {}개)", ticker, firstSize);
				return null;
			}

            // 2. 두 번째 API 호출을 위한 기준일 추출 (첫 번째 데이터의 마지막 날짜)
            String lastXymd = firstOutput.get(99).path("xymd").asText();
            if (lastXymd.isEmpty()) return null;

            
            //
            String today = firstOutput.get(0).get("xymd").asText();
            String toPrice = firstOutput.get(0).get("clos").asText();
            String prePrice = firstOutput.get(1).get("clos").asText();
			if(!todayStr.equalsIgnoreCase(today)) {
				log.info("{} 오늘기준으로 하루전 데이터까지 가져오는중 = {}", "todayStr", todayStr);
			}
            log.info("{} 현재날짜 = {}", "todayStr", todayStr);
			log.info("{} 현재최신수집날짜 = {}", "today", today);
			log.info("{} 현재최신수집날짜로부터 100영업일전 날짜 = {}", "lastXymd", lastXymd);
            //
			
            
            LocalDate targetDate = LocalDate.parse(lastXymd, DATE_FORMATTER);
            String previousDateStr = targetDate.minusDays(1).format(DATE_FORMATTER);

            // 3. 두 번째 API 호출 (과거 100일 데이터)
            JsonNode secondResponse = priceService.getDailyPrice2(ticker, market, previousDateStr, 0);
            if (!isResponseValid(secondResponse)) return null;

			JsonNode secondOutput = secondResponse.get("output2");
			double secondTotal = calculateTotalClose(secondOutput);
			int secondSize = secondOutput.size();

			if (secondSize < 100) {
				log.warn("{}의 과거 데이터가 부족합니다. (수신 데이터: {}개)", ticker, secondSize);
				return null;
			}

			// 4. 200일 이동평균 계산 (총합 / 200)
			double movingAverage200 = (firstTotal + secondTotal) / 200.0;
			log.info("[{}] 200일 이동평균선 계산 완료 = {}", ticker, movingAverage200);

			// json 갱신
			saveJson( fileName,toPrice, prePrice, movingAverage200);
			
			return movingAverage200;

		} catch (Exception e) {
			log.error("[{}] 200일 이동평균선 계산 중 예외 발생: {}", ticker, e.getMessage(), e);
			return null;
		}
	}

    private boolean isResponseValidBasic(JsonNode response) {
		if (response == null || !"0".equals(response.path("rt_cd").asText())) {
			log.warn("API 정상 응답이 아닙니다. 응답 코드: {}", response != null ? response.path("msg1").asText() : "null");
			return false;
		}
		return true;
	}
    
	private boolean isResponseValid(JsonNode response) {
		if (response == null || !"0".equals(response.path("rt_cd").asText())) {
			log.warn("API 정상 응답이 아닙니다. 응답 코드: {}", response != null ? response.path("msg1").asText() : "null");
			return false;
		}
		JsonNode output = response.get("output2");
		return output != null && output.isArray() && !output.isEmpty();
	}
	

	private double calculateTotalClose(JsonNode outputArray) {
		double total = 0;
		for (JsonNode node : outputArray) {
			total += node.path("clos").asDouble();
		}
		return total;
	}
}
