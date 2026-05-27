package com.nc.stock.project.stock.token;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nc.stock.project.stock.config.KisConfig;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
//@RequiredArgsConstructor
public class TokenService {

    private final KisConfig kisConfig;
    private final TokenStore tokenStore;
    private final WebClient webClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /*
    private final WebClient webClient = WebClient.builder()
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
	*/

    
    public TokenService(
			WebClient.Builder webClientBuilder, KisConfig kisConfig ,TokenStore tokenStore
			) {
		this.kisConfig = kisConfig;
		this.tokenStore = tokenStore;
		this.webClient = webClientBuilder
						//.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
						//.baseUrl("https://www.alphavantage.co")
						.baseUrl(kisConfig.getBaseUrl())
						.codecs(configurer -> configurer
								.defaultCodecs()
								.maxInMemorySize(16 * 1024 * 1024)
								) // 16MB로 버퍼 확대
						.build();
		// TODO Auto-generated constructor stub
	}
    
    
    // 미사용 
    public String getAccessToken() {

        Map<String, String> body = new HashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", kisConfig.getAppkey());
        body.put("appsecret", kisConfig.getAppsecret());

        String response = webClient.post()
                .uri(kisConfig.getBaseUrl() + "/oauth2/tokenP")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return response;
    }
    
    public String   getBaseUrl() {
    	 return kisConfig.getBaseUrl();
    }
    
    public String   getInitAccessToken() {
   	 	return kisConfig.getInitAccessToken();
    }
    
    public void initToken() {
    	
    	long expiresIn = 86400;
    	String accessToken = getInitAccessToken();
        tokenStore.updateToken(accessToken, expiresIn);
    }
    
    public void refreshToken() {

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
        body.put("grant_type", "client_credentials");
        body.put("appkey", kisConfig.getAppkey());
        body.put("appsecret", kisConfig.getAppsecret());

        String response = webClient.post()
                .uri("/oauth2/tokenP")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode json = objectMapper.readTree(response);

            String accessToken = json.get("access_token").asText();
            long expiresIn = json.get("expires_in").asLong();

            tokenStore.updateToken(accessToken, expiresIn);
            
            log.info("accessToken : " + accessToken);
            log.info("expiresIn : " + expiresIn);
            
            
        } catch (Exception e) {
            throw new RuntimeException("토큰 파싱 실패", e);
        }
    }
    

    public void revokeToken() {

        Map<String, String> body = new HashMap<>();
        body.put("appkey", kisConfig.getAppkey());
        body.put("appsecret", kisConfig.getAppsecret());
        body.put("token", "페기할 토큰");

        String response = webClient.post()
                .uri("/oauth2/revokeP")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode json = objectMapper.readTree(response);

            String code = json.get("code").asText();
            String message = json.get("message").asText();

            //tokenStore.updateToken(accessToken, expiresIn);
            
            log.info("code : " + code);
            log.info("message : " + message);
            
            
        } catch (Exception e) {
            throw new RuntimeException("토큰 폐기 실패", e);
        }
    }
    
    
    
    
    
    
    
}