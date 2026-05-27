package com.nc.stock.project.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BatchStatusDto {
    private String id;
    private String date;
    private String fileName;    // 배치가 실행하는 전략파일이름
    private String batchFileName;    // 배치파일이름
    private int currentStep;     // 현재 완료한 실행 스텝 (1 ~ 100)
    private int totalExecuteCount;     // 목표 총 실행 횟수 (100)
    private int totalMinutes;      // 제한 시간 분 (5)
    private boolean isCompleted; // 전체 배치 성공 완료 여부
    private int realFailCount;  // 실패한 갯수
    private int orderTotalAmount; //주문 총량
    private int orderRemainedAmount; //남은 주문 총량
    
    private int retry; // 재시도 횟수
    private int test1or0; //테스트유무
    private int buyOrSell; //1or2
    
    // === 실시간 시간 추적을 위해 추가된 필드 ===
    private long startTimeMillis;   // 배치가 처음 시작된 시간 (System.currentTimeMillis())
    private long elapsedSeconds;    // 배치가 시작된 후 현재까지 흘러간 총 시간(초)
    private long remainingSeconds;  // 목표 완수까지 남은 시간(초)
}
