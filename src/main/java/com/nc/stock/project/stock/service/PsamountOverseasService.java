package com.nc.stock.project.stock.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nc.stock.project.stock.config.KisConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PsamountOverseasService {

    private final WebClient kisWebClient;
    private final KisConfig kisConfig;
    private final ObjectMapper objectMapper;

    
    public JsonNode getBalanceP(String orderPrice,String simbol,String office) throws Exception {

        String response = kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/overseas-stock/v1/trading/inquire-psamount")
                        .queryParam("CANO", kisConfig.getAccount())  //계좌번호
                        .queryParam("ACNT_PRDT_CD", "01") //계좌구분
                        .queryParam("OVRS_EXCG_CD", office) //거래소 NASD AMEX
                        .queryParam("OVRS_ORD_UNPR", orderPrice)  //해외주문단가 75.49  (23.8)
                        //.queryParam("ITEM_CD", "SOXQ")  //종목코드  
                        //.queryParam("ITEM_CD", "VGSH")  //종목코드  
                        .queryParam("ITEM_CD", simbol)  //종목코드  VBIL
                        
                        
                        //.queryParam("TR_CRCY_CD", "USD")  //화폐종류
                        //.queryParam("CTX_AREA_FK200", "")  //다음페이지 조회시
                        //.queryParam("CTX_AREA_NK200", "")  //다음페이지 조회시
                        .build())
                .header("tr_id", "TTTS3007R")
                //.header("tr_cont", "")  //연속 거래 여부 N일때 연속조회 
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("해외주식 예수금 응답 = {}", response);

        JsonNode jsonNode = objectMapper.readTree(response);

        return jsonNode;
    }
    

}