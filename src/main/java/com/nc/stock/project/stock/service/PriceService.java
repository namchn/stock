package com.nc.stock.project.stock.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
//@RequiredArgsConstructor
public class PriceService {

    private final WebClient kisWebClient;
    private final WebClient defaultWebClient; // @Qualifier로 수동 매핑할 변수
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 💡 생성자를 통해 스프링에게 정확한 빈을 주입하라고 지시합니다.
    public PriceService(
            WebClient kisWebClient, 
            @Qualifier("defaultWebClient") WebClient defaultWebClient) { // 👈 여기에 @Qualifier를 달아줍니다.
        this.kisWebClient = kisWebClient;
        this.defaultWebClient = defaultWebClient;
    }
    
    //시세 조회
    public JsonNode getPrice(String ticker,String office) throws JsonMappingException, JsonProcessingException {
    	
        String response =	 kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/overseas-price/v1/quotations/price")
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", office)  //NYS : 뉴욕 NAS : 나스닥 AMS : 아멕스
                        .queryParam("SYMB", ticker)  //"AAPL"
                        .build())
                .header("tr_id", "HHDFS00000300")
                .retrieve()
                .bodyToMono(String.class)
                .block();
        
    	JsonNode jsonNode = objectMapper.readTree(response);
        return jsonNode;
    	
    }
    
    
    
    
    
    
    //시세 조회
    public String getDailyPrice(String ticker,String office) {

        // 1. 오늘 날짜 구하기 -> 50일 빼기
        LocalDate fiftyDaysAgo = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(0);//75
        // 2. 원하는 포맷(yyyyMMdd)으로 변환
        String result = fiftyDaysAgo.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        return kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/overseas-price/v1/quotations/dailyprice")
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", office)  //NYS : 뉴욕 NAS : 나스닥 AMS : 아멕스
                        .queryParam("SYMB", ticker)  //"AAPL"
                        .queryParam("GUBN", "0")  // 0 : 일  1 : 주  2 : 월 
                        .queryParam("BYMD", result)  //20260328
                        .queryParam("MODP", "1") //수정주가 0 : 미반영 1 : 반영 1
                        .build())
                .header("tr_id", "HHDFS76240000")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
    
    //시세 조회
    public JsonNode getDailyPrice2(String ticker,String office,String day,int minusDays) throws JsonMappingException, JsonProcessingException {

        // 1. 오늘 날짜 구하기 -> 50일 빼기
        LocalDate fiftyDaysAgo = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(minusDays);//75
        // 2. 원하는 포맷(yyyyMMdd)으로 변환
        String result = fiftyDaysAgo.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        	 String response =				
        		kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/overseas-price/v1/quotations/dailyprice")
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", office)  //NYS : 뉴욕 NAS : 나스닥 AMS : 아멕스
                        .queryParam("SYMB", ticker)  //"AAPL"
                        .queryParam("GUBN", "0")  // 0 : 일  1 : 주  2 : 월 
                        .queryParam("BYMD", day)  //20260328
                        .queryParam("MODP", "1") //수정주가 0 : 미반영 1 : 반영 1
                        .build())
                .header("tr_id", "HHDFS76240000")
                .retrieve()
                .bodyToMono(String.class)
                .block();
        		
             //log.info("해외주식 잔고조회 응답 = {}", response);
             JsonNode jsonNode = objectMapper.readTree(response);
             return jsonNode;
    }
    
    /**
     * [예시] 외부 알림 API 호출 (지연 없이 즉시 발사되는 공통 WebClient 사용)
     */
    public void sendTelegramAlert(Map body) {
        defaultWebClient.post()
                .uri("https://telegram.org<토큰>/sendMessage")
                .bodyValue(body)  //new TelegramPayload
                .retrieve()
                .bodyToMono(String.class)
                .block(); // 한투증 제한(100ms)과 상관없이 0ms만에 즉시 실행됩니다.
    }
    
    
}