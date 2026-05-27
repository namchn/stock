package com.nc.stock.project.stock.token;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

@Component
public class TokenStore {

    private String accessToken;
    private LocalDateTime expiredAt;

    public synchronized void updateToken(String token, long expiresIn) {
        this.accessToken = token;
        this.expiredAt = LocalDateTime.now().plusSeconds(expiresIn - 60); // 1분 여유
    }

    public String getAccessToken() {
        return accessToken;
    }

    public boolean isExpired() {
        return accessToken == null || expiredAt == null || expiredAt.isBefore(LocalDateTime.now());
    }
}