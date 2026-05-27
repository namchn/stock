package com.nc.stock.project.stock.schedule;

import java.util.stream.StreamSupport;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.nc.stock.project.stock.config.KisConfig;
import com.nc.stock.project.stock.context.MultiBatchOrderService;
import com.nc.stock.project.stock.dto.StockInfoDto;
import com.nc.stock.project.stock.facade.AccountAnalysisService;
import com.nc.stock.project.stock.facade.StockAnalysisService;
import com.nc.stock.project.stock.json.InfoJsonFileService;
import com.nc.stock.project.stock.listener.DefaultEventListener.EmailSendEvent;
import com.nc.stock.project.stock.service.ForeignMarginOverseasService;
import com.nc.stock.project.stock.service.OrderService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
public class StockScheduler {

	
	private final StockAnalysisService stockAnalysisService; 
	
	
    private final OrderService orderService; 
    private final AccountAnalysisService accountAnalysisService; 
    //private final PsamountOverseasService psamountOverseasService; 
    private final ForeignMarginOverseasService foreignMarginOverseasService; 
    private final InfoJsonFileService infoJsonFileService; 
    //private final PriceService priceService; 
    //private final EmailService emailService; 
    private final KisConfig kisConfig; 
    
    //private final BatchOrderService batchOrderService; 
    private final MultiBatchOrderService multiBatchOrderService; 
    //private final DefaultEventListener defaultEventListener; 
    private final ApplicationEventPublisher publisher;
    

    // 1시간마다 실행
    //@Scheduled(fixedDelay = 60 * 60 * 1000)
    // 서버 시작 1시간 뒤부터 실행
    //@Scheduled(initialDelay = 60 * 60 * 1000,fixedDelay = 60 * 60 * 1000)
    
    
    // 서버 시작 시 1회 실행
    @PostConstruct
    public void init() {
    	
    	/*
    	String initAccessToken =  tokenService.getInitAccessToken();
    	if(initAccessToken.length()>0) {
    		tokenService.initToken();
    		log.info("=initAccessToken=");
    	}else {
            tokenService.refreshToken();
            log.info("=refreshToken=");
    	}
    	*/
    	log.info("=======================까꿍!==========================");
    }

    //전일자 기준 200일선 계산스케쥴링 
    //                 초 분 시 
    @Scheduled(cron = "10 05 22 * * MON-FRI", zone = "Asia/Seoul")
    //@Scheduled(cron = "1 * * * * *", zone = "Asia/Seoul")
    public void getDailyPriceScheduler() {
        long startTime = System.nanoTime();
        log.info("=== 200일 이평선 수집 스케줄러 시작 ===");

        // TQQQ 주식의 200일 이평선 계산 요청
        Double tqqqAverage = stockAnalysisService.calculate200DayMovingAverage("TQQQ","TQQQ", "NAS",1); //"TQQQ","NAS" //하루전날 데이터수집
        
        // 계산값 json 에 적재
        //stockAnalysisService.saveJson("TQQQ",tqqqAverage);

        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1_000_000;
        log.info("==={} 스케줄러 종료 (실행 시간: {} ms, 결과 존재 여부: {}) ===","getDailyPriceScheduler", duration, (tqqqAverage != null));

        
        EmailSendEvent event = new EmailSendEvent(
			        		kisConfig.getMailMe()
			        		, "제목: [" + "200일 이평선 수집" + "]"
			        		, "내용 생략");
        publisher.publishEvent(event);
    }

    //주문 가능 금액/수량 확인
    //                 초 분 시 
    @Scheduled(cron = "10 10 22 * * MON-FRI", zone = "Asia/Seoul")
   // @Scheduled(cron = "3 * * * * *", zone = "Asia/Seoul")
    public void psamountScheduler() {
    	long startTime = System.nanoTime();
    	log.info("=== 주문 가능 금액/수량 확인 스케줄러 시작 ===");

        // TQQQ 종목에 대한 주문 가능 정보 조회 호출
    	accountAnalysisService.SetOrderAbleAmount("TQQQ", "TQQQ","0.0", "NASD"); //NASD : 나스닥 / NYSE : 뉴욕 / AMEX : 아멕스
		
    	long endTime = System.nanoTime();
		long duration = (endTime - startTime) / 1_000_000;
		log.info("=== {} 스케줄러 종료 (실행 시간: {} ms, 결과 존재 여부: {}) ===","psamountScheduler", duration, true);
    	
    }
    
    // 계좌 매도 가능 금액/수량 확인
    @Scheduled(cron = "10 20 22 * * MON-FRI", zone = "Asia/Seoul")
    //@Scheduled(cron = "3 * * * * *", zone = "Asia/Seoul")
    public void balanceScheduler() {
    	long startTime = System.nanoTime();
    	log.info("=== 계좌 매도 가능 금액/수량 확인 스케줄러 시작 ===");

    	accountAnalysisService.setBalanceAmount("TQQQ", "TQQQ","0.0", "NASD"); //NASD : 나스닥 / NYSE : 뉴욕 / AMEX : 아멕스
		
    	long endTime = System.nanoTime();
		long duration = (endTime - startTime) / 1_000_000;
		log.info("=== {} 스케줄러 종료 (실행 시간: {} ms, 결과 존재 여부: {}) ===","balanceScheduler", duration, true);
    	
    }
    
    
    // 대량매매 수행
    // 주중 오후 11시 30분에 배치 프로세스 시작
    @Scheduled(cron = "10 45 23 * * MON-FRI", zone = "Asia/Seoul")
    //@Scheduled(cron = "10 59 15 * * MON-FRI", zone = "Asia/Seoul")
    public void startBatch() {
        boolean test = false; //true false 
    	
    	String strategyFileName = "TQQQ";
    	String ticker = "TQQQ";
    	
    	int totalExecuteCount = 300; // 총 실행 횟수
        int totalMinutes = 300;    // 제한 5시간 ,300분
        int orderTotalAmount=0; //주문 총량
        int tradeSignal = stockAnalysisService.confirmOrderSignal(strategyFileName, ticker);

        
        int test1or0=0;
        if(test) {
        	test1or0=1; // 테스트 유무  1일때 
        	//totalMinutes=1;  //  총 시간 
        	tradeSignal=2;     // 1:매수 2:매도  
        	//strategyFileName = "SPYM"; 
        	//ticker = "SPYM";
        }
        
        if(tradeSignal==1) { //매수시그널
        	log.info("{} 대량 매수 시그널 발생",strategyFileName);
        	orderTotalAmount = stockAnalysisService.getOrderStock(strategyFileName, ticker);
            if(orderTotalAmount!=0)multiBatchOrderService.runOrderBatchTask(strategyFileName,totalExecuteCount, totalMinutes,orderTotalAmount,tradeSignal,1,test1or0);
            else log.info(" 매수 가능한 갯수가 {} ",orderTotalAmount);
        }
        if(tradeSignal==2) { //매도시그널
        	log.info("{} 대량 매도 시그널 발생",strategyFileName);
        	orderTotalAmount = stockAnalysisService.getPositionStock(strategyFileName, ticker);
            if(orderTotalAmount!=0)multiBatchOrderService.runOrderBatchTask(strategyFileName,totalExecuteCount, totalMinutes,orderTotalAmount,tradeSignal,1,test1or0);
            else log.info(" 매도 가능한 갯수가 {} ",orderTotalAmount);
        }
    }
    
    

    // 0초 32분 21시 
    //@Scheduled(cron = "10 30 23 * * MON-FRI", zone = "Asia/Seoul")
    public void executeOrderLogic() {
    	//NASD : 나스닥 NYSE : 뉴욕 AMEX : 아멕스
    	orderService.postOrder("SPYM","AMEX","50.12","1","00","BUY");  // SPYM "AMEX"
        log.info("=buy@Scheduled=");
    }
    
    
    // 최초 프로젝트 시작 기념으로 남겨둠.
    //                 초 분 시 
    //@Scheduled(cron = "5 * * * * *", zone = "Asia/Seoul")
    public void foreignMargin() {
    	long startTime = System.nanoTime();
    	log.info("=== foreignMargin 스케줄러 시작 ===");
    	
    	JsonNode jsonNode;
		try {
			jsonNode = foreignMarginOverseasService.getBalanceM();
			if (jsonNode == null) {
	            log.warn("API 응답 결과가 null입니다.");
	            return;
	        }
			//jsonNode = objectMapper.readTree(response);
	    	
	        //String currentPrice =jsonNode.get("output").get("last").asText();
	        
	        long size =jsonNode.get("output").size();
	        
	        String rt_cd  =jsonNode.get("rt_cd").asText();
	        log.info("{} 현재가 = {}","rt_cd", rt_cd);
	        
	        if (!"0".equalsIgnoreCase(rt_cd)) {
	            log.warn("정상 응답이 아닙니다. 에러 코드: {}", rt_cd);
	            return;
	        }
	        
	        if(rt_cd.equalsIgnoreCase("0")) {

		        //jsonNode.path("output").path("last").asText()
		        log.info("{} 현재가 = {}","size", size);
		        
		        
		        if (jsonNode.has("output") && jsonNode.get("output").isArray() && !jsonNode.get("output").isEmpty()) {
		            JsonNode firstItem = jsonNode.get("output").get(0);
			        log.info("{} 현재가 = {}","firstItem", firstItem);
		        }else {
		        	log.warn("output 노드가 없거나 배열 형식이 아닙니다.");
		            return;
		        }
		        
		        JsonNode outputArray = jsonNode.get("output");
				JsonNode usaNode = StreamSupport.stream(outputArray.spliterator(), false)
				    .filter(node -> "미국".equals(node.path("natn_name").asText()))
				    .findFirst()
				    .orElse(null); // 찾지 못했을 경우 null 반환 (또는 다른 기본값 설정)
			
				if (usaNode == null) {
		            log.warn("출력 데이터 중 '미국' 자산 정보를 찾을 수 없습니다.");
		            return;
		        }	
				log.info("{} 현재가 = {}","usaNode", usaNode);
				 
				String frcr_gnrl_ord_psbl_amt  = usaNode.get("frcr_gnrl_ord_psbl_amt").asText();
				if (frcr_gnrl_ord_psbl_amt.isEmpty()) {
		            log.warn("가능 금액(frcr_gnrl_ord_psbl_amt) 데이터가 비어있습니다.");
		            return;
		        }
				log.info("{} 현재가 = {}","frcr_gnrl_ord_psbl_amt", frcr_gnrl_ord_psbl_amt);
				// frcr_mgn_amt 는 매수 예정 증거금

				Double usableCash = Double.parseDouble(frcr_gnrl_ord_psbl_amt);
				StockInfoDto data = infoJsonFileService.initJsonFile("StockData");
				data.setUsableCash(usableCash);
				infoJsonFileService.updateJsonFile("StockData", data);
				// log.info("{} 현","없네.");
				// StockInfoDto data2 = infoJsonFileService.readJsonFile("StockData");
				// log.info("{} 현재가 = {}","frcr_gnrl_ord_psbl_amt", data2.getUsableCash() );

				 /*
				 
				 1.값을 갱신하고 나서   
				 2.로직에서  값이 신호가 뜨면 (200일선 위) 
				 3.로직에 의해서 매수 또는 매도 로직 발동 
				 
				 */
	        }
	        
	        log.info("=foreignMargin@Scheduled=");
	        
		} catch (Exception e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			log.error(e.getMessage());
		}
		long endTime = System.nanoTime();
		long duration = (endTime - startTime) / 1_000_000;
		log.info("=== {} 스케줄러 종료 (실행 시간: {} ms, 결과 존재 여부: {}) ===","foreignMargin", duration, true);
    	
    }
    
}