package com.example.budgettracker.service;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.budgettracker.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;


@Service 
public class JwtService {

    private final SecretKey signinKey;
    private final long expirationMillis;

    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.signinKey = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(secret));
        this.expirationMillis = expirationMinutes * 60_000l;
    }

    public String generatedToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(signinKey)
                .compact();
    }

    public Claims parseToken(String token) {
        return  Jwts.parser()
                    .verifyWith(signinKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
    }
}
