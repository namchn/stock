package com.nc.stock.project.stock.listener;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.nc.stock.common.email.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
//1. 이벤트 객체 정의
public record BatchCompletionEvent(
 String strategyFile,
 int totalSteps,
 int failCount,
 boolean isSuccess,
 String message
) {}
*/




//2. 비동기 이벤트 리스너 (서비스 레이어와 완벽 분리)
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultEventListener {
	
	//private final ApplicationEventPublisher publisher;  //비동기 이면서 결과순서를 정하고 싶을때 내부로직으로 만들기 -> 은 메소드 내부에 작성으로 대체가능
	
	private final EmailService emailService;
	
	//메일 보내기용
	public record AccomplishEvent(String toMail, String title, String message) {}
	public record EmailSendEvent(String toMail, String title, String message) {}
	public record BatchEndEvent(String toMail, String title, String message) {}
	
	
	@Async("AsyncExecutor") // 완전히 별도의 스레드 풀에서 비동기로 수행하여 배치 주행 스레드를 방해하지 않음
	@Order(1) //시작순서, 비동기때는 시작시 순서만 보장
	@EventListener
	//@TransactionalEventListener :DB트랜잭션의 성공/실패 여부 일때 사용 쓰레드 사용시에는 작동안함.
	public void sendMailEvent(AccomplishEvent m) {
		 log.info("[이벤트 수신] {} 메일 발송", m.title());
	     //String status = context.getIsCompleted() ? "정상 완료" : "강제 중단(실패 초과)";
	     emailService.sendEmail(
	    	   m.toMail()  //toMail
	         , m.title()   //title 
	         , m.message() //messege
	     );
	}
	
	@Async("AsyncExecutor") // 완전히 별도의 스레드 풀에서 비동기로 수행하여 배치 주행 스레드를 방해하지 않음
	@EventListener
	public void sendMailEvent(EmailSendEvent m) {
		 log.info("[이벤트 수신] {} 메일 발송", m.title());
	     //String status = context.getIsCompleted() ? "정상 완료" : "강제 중단(실패 초과)";
	     emailService.sendEmail(
	    	   m.toMail()  //toMail
	         , m.title()   //title 
	         , m.message() //messege
	     );
	}
	
	@Async("AsyncExecutor") // 완전히 별도의 스레드 풀에서 비동기로 수행하여 배치 주행 스레드를 방해하지 않음
	@EventListener
	public void sendMailEvent(BatchEndEvent m) {
		 log.info("[이벤트 수신] {} 메일 발송", m.title());
	     //String status = context.getIsCompleted() ? "정상 완료" : "강제 중단(실패 초과)";
	     emailService.sendEmail(
	    	   m.toMail()  //toMail
	         , m.title()   //title 
	         , m.message() //messege
	     );
	}
	
	
	// 아직 사용되지 않음
	@Async("AsyncExecutor") // 완전히 별도의 스레드 풀에서 비동기로 수행하여 배치 주행 스레드를 방해하지 않음
	@EventListener
	public void handleSendMailEvent(BatchEndEvent m) {
		//log.info("[이벤트 수신] {} 배치 완료 알림 처리 시작", event.strategyFile());
	     //String status = context.getIsCompleted() ? "정상 완료" : "강제 중단(실패 초과)";
	     
	}
}