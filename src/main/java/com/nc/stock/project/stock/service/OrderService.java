package com.nc.stock.project.stock.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nc.stock.project.stock.config.KisConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final WebClient kisWebClient;
    private final KisConfig kisConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode postOrder(String ticker
    						, String office
    						, String orderPrice
    						, String num
    						, String orderType
    						, String buy) {

    	/*
        WebClient client = WebClient.builder()
                .baseUrl(kisConfig.getBaseUrl())
                //.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        */
        
    	/*
    	if(true) {
    		tokenStore.updateToken(kisConfig.getInitAccessToken(), 86400);
    	}
    	else {
    		//스톱
    	}
    	*/
        
        Map<String, String> body = new HashMap<>();
        body.put("CANO", kisConfig.getAccount());
        body.put("ACNT_PRDT_CD", "01");
        body.put("OVRS_EXCG_CD", office);   //NASD : 나스닥 NYSE : 뉴욕 AMEX : 아멕스 "AMEX"
        body.put("PDNO", ticker);  //  "SPYM"
        body.put("ORD_QTY", num);
        body.put("OVRS_ORD_UNPR",orderPrice); //주문가격    시장가:0   //86.6800  
        body.put("CTAC_TLNO", kisConfig.getPhone());
        //body.put("MGCO_APTM_ODNO", "1");  // 운용사지정주문번호
        body.put("SLL_TYPE", buy.equalsIgnoreCase("BUY")?"":"00");  // 00 : 매도
        body.put("ORD_SVR_DVSN_CD", "0");  //주문서버구분코드 "0"(Default)
        body.put("ORD_DVSN", orderType);  // 주문구분  00 : 지정가  01 (시장가)
        //body.put("START_TIME", "YYMMDD ");  //시작시간 YYMMDD 
        //body.put("END_TIME", "YYMMDD ");  //종료시간 YYMMDD 
        //body.put("ORD_DVSN", "00");  // 알고리즘주문시간구분코드  00 : 분할주문 시간 직접입력 , 02 : 정규장 종료시까지
        

        String response = kisWebClient.post()
                .uri("/uapi/overseas-stock/v1/trading/order")
                .bodyValue(body)
                .header("tr_id",buy.equalsIgnoreCase("BUY")?"TTTT1002U":"TTTT1006U") // 매수 TTTT1002U    // 매도 TTTT1006U 
                .retrieve()
                .bodyToMono(String.class)
                .block();
        
        
        //JsonNode jsonNode = objectMapper.readTree(response);
        JsonNode json;

        try {
             json = objectMapper.readTree(response);

            String rt_cd = json.get("rt_cd").asText();
            long msg_cd = json.get("msg_cd").asLong();
            String msg1 = json.get("msg1").asText();

            log.info("rt_cd : " + rt_cd);
            log.info("msg_cd : " + msg_cd);
            log.info("msg1 : " + msg1);
            
            
        } catch (Exception e) {
            throw new RuntimeException("파싱 실패", e);
        }
        
        return json;
    }
    
}