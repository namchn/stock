package com.nc.stock.project.stock.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GlobalStrategyConfigDto {
	private String cronExpression; //  크론 표현식 필드
	private int totalExecuteCount; //  총 수행 횟수
	private int totalMinutes;      //  총 실행 시간
	private boolean testMode;	   //  테스트 모드 
	private List<TargetConfig> targets; 

	@Getter
	@Setter
	public static class TargetConfig {
		private String strategyFileName;
		private String ticker;
	}
}