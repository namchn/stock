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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nc.stock.project.stock.dto.BatchStatusDto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BatchJsonFileService {

	// 클래스 멤버 변수로 락 관리 맵 선언
	private final Map<String, Object> fileLocks = new ConcurrentHashMap<>();

    // [메모리 적재 공간] BatchStatusDto 전용 캐시
    private final Map<String, BatchStatusDto> batchCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 주식 파일과 꼬이지 않도록 하위 경로(batch/)를 명시적으로 분리해 주는 것이 깔끔합니다.
    private final String BASE_DIR = "config/json/batch/";
    
    // 서버운영자 기준 시점
    private final String SERVICE_ZONE = "Asia/Seoul";
    
    /*
    private File getFile(String fileName) {
        return Paths.get(BASE_DIR, fileName + ".json").toFile();
    }
    */

    /**
     * [서버 재기동 시 데이터 복구]
     * 서버가 켜질 때 딱 한 번 실행되며, 기존 디스크의 배치 진행 JSON을 메모리에 전부 올립니다.
     */
    @PostConstruct
    public void initLoadCacheFromDisk() {
        log.info("[시스템 기동] 배치 JSON 파일의 내용을 메모리에 적재 중...");
        Path dirPath = Paths.get(BASE_DIR);
        
        if (!Files.exists(dirPath)) {
            try {
                Files.createDirectories(dirPath);
            } catch (IOException e) {
                log.error("배치 저장 디렉토리 생성 실패", e);
                return;
            }
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "*.json")) {
            int totalCount =0;
        	for (Path entry : stream) {totalCount++;
                String strategyFileName = entry.getFileName().toString();
                //String batchKey = strategyFileName.substring(0, strategyFileName.lastIndexOf(".json")); //
                String batchKey = strategyFileName.replaceAll("(?i)\\.json$", "");
                
                try {
                    BatchStatusDto data = objectMapper.readValue(entry.toFile(), BatchStatusDto.class);
                    if(!data.isCompleted()) {
                    	log.warn("[시스템 기동] 미동부시간기준 해당날짜의 미완료 배치 파일({})의 내용 로드 - 수행하고자 하는 전략파일이름 : {} ",batchKey ,data.getFileName()==null?"미작성":data.getFileName());
                    	batchCache.put(batchKey, data); // 메모리에 적재
                    }
                    
                } catch (IOException e) {
                    log.error("배치 파일 내용 로드 실패: {}", strategyFileName, e);
                }
            }
            log.info("[시스템 기동] 총 {}/{}개의 미완료 배치 데이터가 메모리에 적재 완료되었습니다.", batchCache.size(),totalCount);
        } catch (IOException e) {
            log.error("배치 디렉토리 읽기 실패 {}", e);
        }
    }

    /**
     * 1. JSON 파일 초기화 및 기본 정보 템플릿 생성
     */
    public BatchStatusDto initJsonFile(String fileName) throws IOException {
        BatchStatusDto dataInit = readJsonFile(fileName);
        BatchStatusDto data = null;     

        ZoneId seoulZone = ZoneId.of(SERVICE_ZONE); //운영자 기준
        DateTimeFormatter saveFormatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
        String formattedDate = ZonedDateTime.now(seoulZone).format(saveFormatter);
         
        if (dataInit != null) {
            log.info("{} .", "배치 진도 데이터 존재: 갱신");
            data = dataInit;
            data.setDate(formattedDate);
        } else {
            log.info("{} .", "배치 진도 데이터 없음: 최초 생성");
            data = new BatchStatusDto(); 
            data.setId(UUID.randomUUID().toString()); // 고유 ID 부여
            data.setDate(formattedDate); 
        }
        
        return data;
    }
    
    /**
     * JSON 파일 업데이트
     */
    public void updateJsonFile(String fileName, BatchStatusDto data) throws IOException {
        createJsonFile(fileName, data);
    }
    
    /**
     * JSON 파일 생성 및 메모리 동시 갱신 (쓰기)
     * 파일 이름별로 가두어 동시성 충돌을 완벽히 방어합니다.
     */
    public void createJsonFile(String fileName, BatchStatusDto data) throws IOException {
        // 1. 메모리 데이터 즉시 갱신
        batchCache.put(fileName, data);
    	
        Path path = Paths.get(BASE_DIR);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        
        // 2. fileName 전용 가벼운 락 객체 획득 (메모리 누수 없음)
        Object lock = fileLocks.computeIfAbsent(fileName, k -> new Object());
        
        // 2. 디스크 파일 쓰기 동기화 (원자성 확보)
        synchronized (lock) {  //fileName.intern() 는 메모리 누수위험
        	try {
        	
            File file = Paths.get(BASE_DIR, fileName + ".json").toFile();
            File tempFile = Paths.get(BASE_DIR, fileName + ".tmp").toFile();
            
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile, data);
            //objectMapper.writeValue(tempFile, data);  // 미세한 성능 향상위해서는
            
            Files.move(tempFile.toPath(), file.toPath(), 
                StandardCopyOption.REPLACE_EXISTING, 
                StandardCopyOption.ATOMIC_MOVE);
            
	        } catch (IOException e) {
                log.error("배치 파일 내용 로드 실패: {} // {}", lock.toString(), e);
            } finally {
	            // [옵션] 사용이 끝난 락은 맵에서 지워 메모리를 깔끔하게 관리합니다.
	            //fileLocks.remove(fileName);   (a -> b) ->c 접근시 b가 오래된 객체를 얻어 동시성 깨질 우려
	        }
        }
    }

    /**
     * 2. 메모리에서 즉시 읽기 (I/O 없음, Lock 없음, 지연 속도 0ms)
     * 
     */
    public BatchStatusDto readJsonFile(String fileName) throws IOException {
        return batchCache.get(fileName);
    	
        /*
		//외부 스레드가 원본 캐시 객체를 함부로 훼손하지 못하도록 얕은/깊은 복사 반환
    	BatchStatusDto cached = batchCache.get(fileName);
        if (cached == null) return null;
        
        // 캐시 원본 객체 손상을 막기 위해 새로운 DTO 객체에 복사하여 반환 (안전망)
        try {
            return objectMapper.copy().readValue(objectMapper.writeValueAsString(cached), BatchStatusDto.class);
        } catch (Exception e) {
            return cached; // 실패 시 복원용 폴백
        }
        */
    }
    
    /*
    public List<BatchStatusDto> readJsonFiles(String fileName) throws IOException {
        
    	
    	return batchCache.get(fileName);
    }
    */
    
    public List<BatchStatusDto> readJsonFilesContaining(String keyword) {
        return batchCache.keySet().stream()                 // 1. 캐시의 전체 파일명(Key) 스트림 생성
                .filter(fileName -> fileName.toLowerCase().contains(keyword)) // 2. 파일명에 키워드가 포함되는지 필터링
                .map(batchCache::get)                         // 3. 일치하는 파일명의 BatchStatusDto 가져오기
                .collect(Collectors.toList());                // 4. 결과를 리스트로 담아서 반환
    }
    
    public List<BatchStatusDto> readJsonFilesAll() {
        return batchCache.keySet().stream()                 // 1. 캐시의 전체 파일명(Key) 스트림 생성
                .map(batchCache::get)                         // 3. 일치하는 파일명의 BatchStatusDto 가져오기
                .collect(Collectors.toList());                // 4. 결과를 리스트로 담아서 반환
    }

    /**
     * 3. JSON 파일 삭제 및 메모리 클리어
     */
    public boolean deleteJsonFile(String fileName) {
        batchCache.remove(fileName); // 메모리 캐시 삭제
        synchronized (fileName.intern()) {
            File file = Paths.get(BASE_DIR, fileName + ".json").toFile();
            if (file.exists()) {
                return file.delete();
            }
            return false;
        }
    }
    
    

    /**
     * [추가된 핵심 기능] 10일 지난 완료 배치 파일 자동 삭제 (매일 새벽 2시 실행)
     * 주말(토, 일)을 제외한 월~금 아침 8시에 디스크의 배치 파일들을 검사합니다.
     */
    @Scheduled(cron = "0 0 8 * * MON-FRI", zone = "Asia/Seoul")
    //@Scheduled(cron = "10 17 13 * * MON-FRI", zone = "Asia/Seoul")
    public void cleanOldCompletedBatches() {
        log.info("[배치 클리너] 오래된 완료 파일 정리 작업을 시작합니다...");
        Path dirPath = Paths.get(BASE_DIR);
        long dayago = 10L; //10일
        
        if (!Files.exists(dirPath)) return;

        long tenDaysAgoMillis = System.currentTimeMillis() - (dayago * 24 * 60 * 60 * 1000);
        int deleteCount = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "*.json")) {
            for (Path entry : stream) {
                File file = entry.toFile();
                
                // 파일의 마지막 수정 시간이 10일보다 더 과거인지 확인
                if (file.lastModified() < tenDaysAgoMillis) {
                    try {
                        // 먼저 내부 데이터를 읽어 완주된 파일(isCompleted == true)인지 검증
                        BatchStatusDto data = objectMapper.readValue(file, BatchStatusDto.class);
                        
                        if (data != null && data.isCompleted()) {
                            String batchKey = file.getName().substring(0, file.getName().lastIndexOf(".json"));
                            
                            // 1. 메모리 캐시에서 제거
                            batchCache.remove(batchKey);
                            
                            // 2. 동시성 락을 잡고 안전하게 디스크에서 물리 삭제
                            synchronized (batchKey.intern()) {
                                if (file.delete()) {
                                    deleteCount++;
                                    log.info("[배치 클리너 삭제 완료] {}일 경과 파일 제거됨: {}",dayago, file.getName());
                                }
                            }
                        }
                    } catch (IOException e) {
                        log.error("클리닝 검사 중 파일 읽기 실패: {}", file.getName(), e);
                    }
                }
            }
            log.info("[배치 클리너 종료] 총 {}개의 오래된 완료 파일이 정리되었습니다.", deleteCount);
        } catch (IOException e) {
            log.error("배치 디렉토리 스캔 실패", e);
        }
    }
}
