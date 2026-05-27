package com.nc.stock.project.stock.facade;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nc.stock.project.stock.dto.StockInfoDto;
import com.nc.stock.project.stock.json.InfoJsonFileService;
import com.nc.stock.project.stock.service.OverseasBalanceAccountService;
import com.nc.stock.project.stock.service.PriceService;
import com.nc.stock.project.stock.service.PsamountOverseasService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountAnalysisService {

	
    private final InfoJsonFileService infoJsonFileService;
    private final PsamountOverseasService psamountOverseasService;
    private final OverseasBalanceAccountService overseasBalanceAccountService;
    private final PriceService priceService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");


    /**
     * json 에 적재
     */
    public void saveJson(String fileName,String ticker,String market, String ord_psbl_qty, Double usableCash) {
    	StockInfoDto data;
		try {
			data = infoJsonFileService.initJsonFile(fileName);
			data.setOrderStock(ord_psbl_qty);
			data.setUsableCash(usableCash);
			data.setTicker(ticker);
			data.setTradeOffice(market);
			infoJsonFileService.updateJsonFile(fileName, data);
		} catch (IOException e) {
			log.error(e.getMessage());
		}
    }
    
    /**
     * json 에 적재
     */
    public void saveBalaceJson(String fileName,String ticker,String market, String ord_psbl_qty ,Double pchs_avg_pric, Double now_pric2,Double totalStockAsset) {
    	StockInfoDto data;
		try {
			data = infoJsonFileService.initJsonFile(fileName);
			data.setPositionStock(ord_psbl_qty);
			data.setAveragePrice(pchs_avg_pric);
			//data.setNowPrice(now_pric2);
			//data.setTicker(ticker);
			//data.setTradeOffice(market);
			data.setTotalStockAsset(totalStockAsset);
			infoJsonFileService.updateJsonFile(fileName, data);
		} catch (IOException e) {
			log.error(e.getMessage());
		}
    }

    /**
     * 내계좌의 특정 종목의 매도 가능 금액 및 수량을 조회합니다.
     * @param price 계산 기준 가격 (예: "10.49")
     * @param ticker 종목 코드 (예: "TQQQ")
     * @param market 시장 코드 (예: "NASD")
     */
    public void setBalanceAmount(String fileName, String ticker,String price, String market) {
    	
    	try {
    		StockInfoDto data = infoJsonFileService.initJsonFile(fileName);
    		if(price.equals("0.0") && data.getNowPrice() != null) {
    			price =  String.valueOf(data.getNowPrice());
    		}
        	
            //JsonNode jsonNode = psamountOverseasService.getBalanceP(price, ticker, market);
            JsonNode jsonNode = overseasBalanceAccountService.getBalance();
            
            if (!isResponseBasicValid(jsonNode)) {return;}
            if (!isResponseBalanceValid(jsonNode)) {return;}// output 노드 존재 여부 검증

            //ticker = "SPYM";
            JsonNode outputArray = jsonNode.get("output1");
            String ovrs_pdno ="";  //티커
            String ovrs_item_name =""; //공식네임
            String ord_psbl_qty ="0";  // 매도가능갯수
            String now_pric2="0";     //현재가격
            double pchs_avg_pric=0.0;   //보유평균가
            double frcr_pchs_amt1=0.0;  //보유평균가
            double now_price=0.0;  //현재가격
            //double total = 0;
    		for (JsonNode node : outputArray) {
    			ovrs_pdno = node.path("ovrs_pdno").asText();
    			if(ovrs_pdno.equalsIgnoreCase(ticker)) {
        			ovrs_item_name = node.path("ovrs_item_name").asText();
        			ord_psbl_qty = node.path("ord_psbl_qty").asText();
        			now_pric2 = node.path("now_pric2").asText();
        			now_price = Double.parseDouble(now_pric2);
        			pchs_avg_pric = node.path("pchs_avg_pric").asDouble();
        			frcr_pchs_amt1 = node.path("frcr_pchs_amt1").asDouble();
    	            log.info("[{}] 조회 성공 - ovrs_item_name: {}, ord_psbl_qty: {}, now_pric2: {}, pchs_avg_pric: {}, frcr_pchs_amt1: {}"
    	            		, ovrs_pdno,ovrs_item_name,ord_psbl_qty,now_pric2, pchs_avg_pric, frcr_pchs_amt1);

    	            //Double usableCash = Double.parseDouble(ovrs_ord_psbl_amt);
            		//saveBalaceJson(fileName,ticker,market,ord_psbl_qty,pchs_avg_pric,now_price);
    			}
    		}
    		if(ord_psbl_qty.equals("0")) log.info("매도가능한 {} 가 없음",ticker);

			/*
			 * {"frcr_pchs_amt1":"163.46995"
			 * ,"ovrs_rlzt_pfls_amt":"-0.33749"
			 * ,"ovrs_tot_pfls":"2.51505"
			 * ,"rlzt_erng_rt":"-0.43710659"
			 * ,"tot_evlu_pfls_amt":"165.98500000"
			 * ,"tot_pftrt":"1.53853965"
			 * ,"frcr_buy_amt_smtl1":"77.210000"
			 * ,"ovrs_rlzt_pfls_amt2":"-507.41622"
			 * ,"frcr_buy_amt_smtl2":"116085.235000"}
			 */
            
            JsonNode output2 = jsonNode.get("output2");
            log.info("전체주식자산 {}" , output2);
            double totalStockAsset=0.0;
            totalStockAsset=output2.path("tot_evlu_pfls_amt").asDouble();
            
    		
    		
    		saveBalaceJson(fileName,ticker,market,ord_psbl_qty,pchs_avg_pric,now_price,totalStockAsset);	
    		
            // 3. 주문 가능 금액 및 수량 데이터 추출
            //String ovrs_ord_psbl_amt = jsonNode.get("output1").get("ovrs_ord_psbl_amt").asText();
        	//String ord_psbl_qty = jsonNode.get("output2").get("ord_psbl_qty").asText();

            //log.info("[{}] 조회 성공 - 가능 금액: {}, 가능 수량: {}", ticker, ovrs_ord_psbl_amt, ord_psbl_qty);

        	//Double usableCash = Double.parseDouble(ovrs_ord_psbl_amt);
        	//saveJson(fileName,ticker,market,ord_psbl_qty,usableCash);
        	
            //return new OrderOrderableAmountDto(overseasOrderableAmount, orderableQuantity);

        } catch (Exception e) {
            log.error("[{}] 주문 가능 금액 조회 중 예외 발생: {}", ticker, e.getMessage(), e);
            //return null;
        }
    }

    /**
     * 특정 종목의 주문 가능 금액 및 수량을 조회합니다.
     * @param price 계산 기준 가격 (예: "10.49")
     * @param ticker 종목 코드 (예: "TQQQ")
     * @param market 시장 코드 (예: "NASD")
     */
    public void SetOrderAbleAmount(String fileName, String ticker,String price, String market) {
    	
    	try {
    		StockInfoDto data = infoJsonFileService.initJsonFile(fileName);
    		if(price.equals("0.0") && data.getNowPrice() != null) {
    			price =  String.valueOf(data.getNowPrice());
    		}
        	
            JsonNode jsonNode = psamountOverseasService.getBalanceP(price, ticker, market);
            
            String ovrs_ord_psbl_amt ="0";
            String ord_psbl_qty =	"0"	;
            if (!isResponseValid(jsonNode)) {
            	// output 노드 존재 여부 검증
            	log.warn("[{}] 응답 데이터에 output 노드가 없습니다.", ticker);
            }else {
                // 3. 주문 가능 금액 및 수량 데이터 추출
                ovrs_ord_psbl_amt = jsonNode.get("output").get("ovrs_ord_psbl_amt").asText();
            	ord_psbl_qty = jsonNode.get("output").get("ord_psbl_qty").asText();
            }
            
            log.info("[{}] 조회 성공 - 가능 금액: {}, 가능 수량: {}", ticker, ovrs_ord_psbl_amt, ord_psbl_qty);

        	Double usableCash = Double.parseDouble(ovrs_ord_psbl_amt);
        	saveJson(fileName,ticker,market,ord_psbl_qty,usableCash);
        	
            //return new OrderOrderableAmountDto(overseasOrderableAmount, orderableQuantity);

        } catch (Exception e) {
            log.error("[{}] 주문 가능 금액 조회 중 예외 발생: {}", ticker, e.getMessage(), e);
            //return null;
        }
    }
    
    private boolean isResponseBasicValid(JsonNode response) {
		if (response == null || !"0".equals(response.path("rt_cd").asText())) {
			log.warn("API 정상 응답이 아닙니다. 응답 코드: {}", response != null ? response.path("msg1").asText() : "null");
			return false;
		}
		return true;
	}
    
    private boolean isResponseBalanceValid(JsonNode response) {
    	// 2. output1 노드 존재 여부 검증
    	JsonNode output1 = response.get("output1");
    	if (output1 == null || !output1.isArray() || output1.isEmpty()) {
			log.warn("API 정상 응답이 아닙니다. 응답 코드: {}", response != null ? response.path("msg1").asText() : "null");
			log.warn("[{}] 응답 데이터에 output1 노드가 없습니다.", "output1");
			return false;
		}
    	// output2 노드 존재 여부 검증
    	JsonNode output2 = response.get("output2");
    	if (output2 == null || output2.isMissingNode() || output2.isEmpty()) {
			log.warn("API 정상 응답이 아닙니다. 응답 코드: {}", response != null ? response.path("msg1").asText() : "null");
			log.warn("[{}] 응답 데이터에 output2 노드가 없습니다.", "output2");
			return false;
		}
    	return true;
	}
    
    private boolean isResponseValid(JsonNode response) {
		if (response == null || !"0".equals(response.path("rt_cd").asText())) {
			log.warn("API 정상 응답이 아닙니다. 응답 코드: {}", response != null ? response.path("msg1").asText() : "null");
			return false;
		}
		
        // 2. output 노드 존재 여부 검증
		JsonNode output = response.get("output");
		return output != null && !output.isMissingNode() && !output.isEmpty();
		// log.warn("[{}] 응답 데이터에 output 노드가 없습니다.", ticker);
	}
    
}