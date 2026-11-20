package com.olawale.budgettracker.service;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.olawale.budgettracker.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessMinutes;
    private final long refreshMinutes;

    public JwtService(@Value("${app.jwt.secret}") String base64Secret,
            @Value("${app.jwt.access-minutes}") long accessMinutes,
            @Value("${app.jwt.refresh-minutes}") long refreshMinutes) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.accessMinutes = accessMinutes;
        this.refreshMinutes = refreshMinutes;
    }

    public long accessTtlSeconds() {
        return accessMinutes * 60;
    }

    public long refreshTtlSeconds() {
        return refreshMinutes * 60;
    }

    public String issueAccessToken(User user) {
        return baseBuilder(user, "access", accessMinutes).compact();
    }

    /**
     * Refresh tokens carry:
     *  - jti: a random id, so two tokens minted in the same second are never identical
     *         (JWT exp has only second precision — without jti, hashes could collide).
     *  - fid: the token FAMILY id. Every rotated successor keeps the same fid, which is
     *         what lets us revoke an entire lineage when reuse of a spent token is detected.
     */
    public String issueRefreshToken(User user, UUID familyId) {
        return baseBuilder(user, "refresh", refreshMinutes)
                .id(UUID.randomUUID().toString())
                .claim("fid", familyId.toString())
                .compact();
    }

    public Optional<Claims> verify(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();

            if (!expectedType.equals(claims.get("type", String.class))) {
                return Optional.empty();
            }

            return Optional.of(claims);
        } catch (Exception e) {
            // bad signature, expiry, malformed — one indistinguishable failure, never a 500
            return Optional.empty();
        }
    }

    private JwtBuilder baseBuilder(User user, String type, long minutes) {
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", "USER")
                .claim("type", type)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + minutes * 60_000))
                .signWith(key);
    }
}
