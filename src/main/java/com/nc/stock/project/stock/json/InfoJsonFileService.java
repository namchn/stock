package com.nc.stock.project.stock.json;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nc.stock.project.stock.dto.StockInfoDto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class InfoJsonFileService {
	// 락 다중서버로 분산락이 필요할경우는  redis와 같은 외부 저장소를 도입하세용 
	//private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    // [메모리 적재 공간] 읽기 전용 캐시 역할을 합니다.
    private final Map<String, StockInfoDto> stockCache = new ConcurrentHashMap<>();


    private final ObjectMapper objectMapper = new ObjectMapper();
    // 파일이 저장될 기본 디렉토리 경로
    //private final String BASE_DIR = "src/main/resources/json/";
    private final String BASE_DIR = "config/json/";
    //private final String BASE_DIR = "/json/";
    //private final String BASE_DIR = "/app/json/";
    
    //@Value("${file.upload-dir:./default_json/}")
    //private String baseDir;
    
    private File getFile(String fileName) {
        return Paths.get(BASE_DIR, fileName + ".json").toFile();
    }

    
    /**
     * [서버 재기동 시 데이터 복구]
     * 서버가 켜질 때 딱 한 번 실행되며, 기존 디스크의 JSON들을 메모리에 전부 올립니다.
     */
    @PostConstruct
    public void initLoadCacheFromDisk() {
        log.info("[시스템 기동] JSON 파일을 메모리에 적재 중...");
        Path dirPath = Paths.get(BASE_DIR);
        
        if (!Files.exists(dirPath)) {
            try {
                Files.createDirectories(dirPath);
            } catch (IOException e) {
                log.error("저장 디렉토리 생성 실패", e);
                return;
            }
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "*.json")) {
            for (Path entry : stream) {
                String fileName = entry.getFileName().toString();
                String stockCode = fileName.substring(0, fileName.lastIndexOf(".json"));
                
                try {
                    StockInfoDto data = objectMapper.readValue(entry.toFile(), StockInfoDto.class);
                    stockCache.put(stockCode, data); // 메모리에 적재
                } catch (IOException e) {
                    log.error("파일 로드 실패: {}", fileName, e);
                }
            }
            log.info("[시스템 기동] 총 {}개의 종목 데이터가 메모리에 적재 완료되었습니다.", stockCache.size());
        } catch (IOException e) {
            log.error("디렉토리 읽기 실패", e);
        }
    }

    
    /**
     * 1. JSON 파일 업데이트
     * 
     */
    public StockInfoDto initJsonFile(String fileName) throws IOException {
    	StockInfoDto dataInit = readJsonFile(fileName);
    	StockInfoDto data = null; 	

		// data.setDate(LocalDateTime.now().toString()); // 현재 시간 저장
		// 1. 서울 시간대 및 포맷 정의
		ZoneId seoulZone = ZoneId.of("Asia/Seoul");
		DateTimeFormatter saveFormatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
		// 2. 현재 서울 시간을 기준으로 포맷 변환
		String formattedDate = ZonedDateTime.now(seoulZone).format(saveFormatter);
		 
    	if(dataInit != null) {
    		log.info("{} .","있으니까 갱신");
    		data = dataInit;
    		data.setDate(formattedDate);
    	}else {
    		log.info("{} .","없으니까 생성");
	       	data = new StockInfoDto() ; 
	   		data.setId(UUID.randomUUID().toString()); // 고유 ID 부여
			data.setDate(formattedDate); // 3. 데이터 저장 ("20260514 23:01:45")
    	}
    	
    	return data;
    }
    
    
    /**
     * JSON 파일 업데이트
     * 
     */
    public void updateJsonFile(String fileName, StockInfoDto data) throws IOException {
    	StockInfoDto dataInit = readJsonFile(fileName);
    	if(dataInit != null) {
    		createJsonFile( fileName,  data);
    	}else {
    		createJsonFile( fileName,  data);
    	}
    }
    
    
    
    
    /**
     * JSON 파일 생성 및 메모리 동시 갱신 (쓰기)
     * 종목코드별로 가두어 동시성 충돌을 완벽히 방어합니다.
     */
    public void createJsonFile(String fileName, StockInfoDto data) throws IOException {
    	// 1. 메모리 데이터 즉시 갱신 (매매 로직이 즉시 최신값을 볼 수 있도록)
        stockCache.put(fileName, data);
    	
    	// 디렉토리가 없으면 생성
        Path path = Paths.get(BASE_DIR);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }

        /*
    	lock.writeLock().lock(); // 쓰기 락 획득 (다른 읽기/쓰기 블락)
        try {
            // 파일 쓰기 로직 (ObjectMapper.writeValue...)

            File file = getFile(fileName);
            // 객체를 JSON 파일로 직렬화하여 저장 (들여쓰기 포함)
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
        	
        } finally {
            lock.writeLock().unlock(); // 락 해제
        }
        */
        
        // 2. 디스크 파일 쓰기 동기화 (종목 코드별 문자열로 락 점유)
        synchronized (fileName.intern()) {
            File file = Paths.get(BASE_DIR, fileName + ".json").toFile();
            // 안전한 저장을 위한 임시 파일 작성
            File tempFile = Paths.get(BASE_DIR, fileName + ".tmp").toFile();
            
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile, data);
            
            // 파일 교체 연산 (원자성 확보)
            Files.move(tempFile.toPath(), file.toPath(), 
                StandardCopyOption.REPLACE_EXISTING, 
                StandardCopyOption.ATOMIC_MOVE);
        }
    	
    }

    // 2. JSON 파일 읽기
    /**
     * 2. 메모리에서 즉시 읽기 (I/O 없음, Lock 없음, 지연 속도 0ms)
     * 디스크를 전혀 건드리지 않아 매우 안전하고 빠릅니다.
     */
    public StockInfoDto readJsonFile(String fileName) throws IOException {
    	// 캐시에서 가져오되, 데이터가 없으면 null 반환
        return stockCache.get(fileName);
        
    	/*
    	lock.readLock().lock(); // 읽기 락 획득 (동시 읽기 가능, 쓰기는 블락)
        try {
            // 파일 읽기 로직 (ObjectMapper.readValue...)
            File file = getFile(fileName);
            if (!file.exists()) {
                throw new IllegalArgumentException("해당 파일이 존재하지 않습니다: " + fileName);
            }
            // JSON 파일을 객체로 역직렬화
            return objectMapper.readValue(file, StockInfoDto.class);
        } finally {
            lock.readLock().unlock(); // 락 해제
        }
        */
    	
    }

    // 3. JSON 파일 삭제
    public boolean deleteJsonFile(String fileName) {
    	/*
    	lock.writeLock().lock(); // 쓰기 락 획득 (다른 읽기/쓰기 블락)
        try {
            File file = getFile(fileName);
            if (file.exists()) {
                return file.delete();
            }
        } finally {
            lock.writeLock().unlock(); // 락 해제
        }
        return false;
        */
    	stockCache.remove(fileName); // 메모리 캐시 삭제
        synchronized (fileName.intern()) {
            File file = Paths.get(BASE_DIR, fileName + ".json").toFile();
            if (file.exists()) {
                return file.delete();
            }
            return false;
        }
        
    }
}
