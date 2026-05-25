package com.dumble.service.session.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class SystemTokenMinter {

    @Value("${jwt.secret:internal-secret-key-shared-between-services-dumble}")
    private String jwtSecret;

    public String generateSystemToken(String audience) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject("session-service")
                .setIssuer("session")
                .setAudience(audience)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + 60000))
                .signWith(SignatureAlgorithm.HS256, jwtSecret.getBytes())
                .compact();
    }
}