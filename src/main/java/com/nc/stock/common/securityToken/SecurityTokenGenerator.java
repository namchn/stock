package com.nc.stock.common.securityToken;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

@Component
public class SecurityTokenGenerator {
    // 암호학적으로 안전한 난수 생성기 객체 재사용
    private static final SecureRandom secureRandom = new SecureRandom();
    // URL에 서도 안전하게 사용할 수 있는 Base64 인코더
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();
    // 년월일시분초 포맷 설정 (무조건 14자리 고정)
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public static String generateSecureToken() {
        // 32바이트(256비트)의 강력한 난수 공간 확보
        byte[] randomBytes = new byte[32]; 
        secureRandom.nextBytes(randomBytes);
        
        // 문자열 변환 (약 43글자의 예측 불가능한 토큰 생성)
        return base64Encoder.encodeToString(randomBytes); 
    }
    
    public static String generateSecureTimeToken() {
    	// 24 바이트 난수 생성 -> Base64 변환 시 패딩 없이 무조건 '32글자' 고정
    	// 33 바이트 난수 생성 -> 44글자  고정
        byte[] randomBytes = new byte[33]; 
        secureRandom.nextBytes(randomBytes);
        
        // 2. 현재 시간 가져오기 -> 무조건 '14글자' 고정 (예: 20260523164600)
        String datePart = LocalDateTime.now().format(DATE_FORMATTER);
        
        // 3. 결합 (32자 + 14자 = 총 46자 고정)
        // 44글자+ 14글자 총 58자 고정
        return base64Encoder.encodeToString(randomBytes); 
    }
    
    
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12; // GCM 표준 IV 길이
    
    private static final Base64.Decoder base64Decoder = Base64.getUrlDecoder();

    // [중요] 외부 유출 절대 금지! 정확히 32바이트(256비트)의 비밀키여야 합니다.
    // 실무에서는 application.properties 환경변수 등으로 주입받아 사용하세요.
    //@Value("${app.security.secret-key}") //String secretKeyString
    private static final String SECRET_KEY_STRING = "12345678901234567890123456789012"; 
    private static final SecretKey SECRET_KEY = new SecretKeySpec(SECRET_KEY_STRING.getBytes(), "AES");

    
    
    /**
     * 14자리 날짜 문자열을 암호화 (결과물은 무조건 44글자로 고정)
     */
    public static String encryptDate(String dateStr) throws Exception {
        byte[] iv = new byte[IV_LENGTH_BYTE];
        secureRandom.nextBytes(iv); // 암호화할 때마다 매번 바뀌는 무작위 IV 생성

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY, parameterSpec);

        byte[] cipherText = cipher.doFinal(dateStr.getBytes());

        // 복호화를 위해 IV(12바이트)와 암호문(30바이트)을 하나로 결합
        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
        byteBuffer.put(iv);
        byteBuffer.put(cipherText);

        // 총 42바이트 데이터 -> Base64 인코딩 시 무조건 '56글자'로 고정됨
        return base64Encoder.encodeToString(byteBuffer.array());
    }

    /**
     * 암호화된 문자열을 원본 14자리 날짜로 복호화
     */
    public static String decryptDate(String encryptedDateStr) throws Exception {
        byte[] decoded = base64Decoder.decode(encryptedDateStr);

        // IV 분리
        byte[] iv = new byte[IV_LENGTH_BYTE];
        System.arraycopy(decoded, 0, iv, 0, iv.length);

        // 암호문 분리
        byte[] cipherText = new byte[decoded.length - IV_LENGTH_BYTE];
        System.arraycopy(decoded, iv.length, cipherText, 0, cipherText.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY, parameterSpec);

        byte[] decryptedText = cipher.doFinal(cipherText);
        return new String(decryptedText);
    }
    
    
	//  검증을 위해 토큰을 파싱하는 시점
	// 날짜 암호문이 무조건 앞 56글자이므로 index 0부터 56까지 잘라냅니다.
    //String extractedEncryptedDate = finalToken.substring(0, 56);
	// 뒤의 32글자는 순수 무작위 난수 영역입니다.
	//String extractedRandomPart = finalToken.substring(56); 
    
    /*
    // 3. 복호화 실행
    try {
        String originalDate = SecureDateCrypto.decryptDate(extractedEncryptedDate);
        System.out.println("복호화된 원본 날짜: " + originalDate); // "20260523164600"
    } catch (Exception e) {
        // 토큰이 위조되었거나 잘못된 키로 복호화할 경우 예외 발생 (보안 방어)
        System.out.println("유효하지 않거나 위조된 토큰입니다.");
    }
    */
    
    /*
    // 3. 문자열 날짜를 LocalDateTime 객체로 파싱
    LocalDateTime tokenCreatedTime = LocalDateTime.parse(originalDate, DATE_FORMATTER);
    LocalDateTime currentTime = LocalDateTime.now();

    // 4. 시간 차이 계산 (현재 시간 - 토큰 생성 시간)
    Duration duration = Duration.between(tokenCreatedTime, currentTime);
    long minutesPassed = duration.toMinutes();

    // 5. 정확히 30분이 지났는지 검증 (시간이 미래로 조작된 마이너스 값 방어도 포함)
    if (minutesPassed < 0 || minutesPassed > 30) {
        // 토큰 만료됨 (생성된 지 30분이 초과함)
        return false; 
    }
    // 암호가 조작되었거나 위조된 경우 AES-GCM 특성상 반드시 에러가 발생하므로 false 반환
    
    */
    
}
