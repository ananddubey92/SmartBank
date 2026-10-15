package com.project.onlinebankingsystem.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;

@Component
public class JwtUtils {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    // Configurable via application.properties or environment variable JWT_SECRET
    @Value("${jwt.secret:${JWT_SECRET:onlinebankingsystemsecretkey256bitormore_securekey!!!}}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms:${JWT_EXPIRATION_MS:86400000}}")
    private long jwtExpirationMs; // 24 hours default

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        if (!StringUtils.hasText(token) || token.equalsIgnoreCase("null") || token.equalsIgnoreCase("undefined") || token.equalsIgnoreCase("[object Object]")) {
            return false;
        }
        if (token.split("\\.").length != 3) {
            return false;
        }
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            logger.warn("Invalid JWT Token: {}", e.getMessage());
        }
        return false;
    }
}
