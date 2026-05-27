package com.nc.stock.common.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
//@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    
    //@Value("${spring.mail.username}")
    private  String fromAddress;
    
    EmailService(
    		@Value("${spring.mail.username}")String fromAddress,
    		JavaMailSender mailSender
    		){
    	this.fromAddress = fromAddress;
    	this.mailSender = mailSender;
    }

    public void sendEmail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        mailSender.send(message);
    }
}
