package com.olawale.budgettracker.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.olawale.budgettracker.dto.auth.AuthResponse;
import com.olawale.budgettracker.dto.login.LoginRequest;
import com.olawale.budgettracker.dto.register.RegisterRequest;
import com.olawale.budgettracker.entity.RefreshToken;
import com.olawale.budgettracker.entity.User;
import com.olawale.budgettracker.exception.EmailAlreadyExistsException;
import com.olawale.budgettracker.exception.InvalidCredentialsException;
import com.olawale.budgettracker.exception.InvalidTokenException;
import com.olawale.budgettracker.repository.RefreshTokenRepository;
import com.olawale.budgettracker.repository.UserRepository;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    // Real bcrypt hash of "timing-dummy": unknown-email logins burn the same ~100ms
    // as known-email-wrong-password, so response time can't enumerate users.
    private static final String DUMMY_HASH = "$2a$10$4bjiJhw0lbAa5Aho0TDHg.vQXp/vhgebu17MZqPd44KQTbKhNasF.";

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = userRepository.save(User.builder()
                .fullName(request.fullName())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .build());

        log.info("User registered userId={}", user.getId());
        return issueTokenPair(user, UUID.randomUUID()); // registration = authentication: fresh family
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        Optional<User> found = userRepository.findByEmail(email);

        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), DUMMY_HASH); // result intentionally unused
            log.warn("Login failure (unknown email) email={}", email);
            throw new InvalidCredentialsException();
        }

        User user = found.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failure (bad password) userId={}", user.getId());
            throw new InvalidCredentialsException();
        }

        log.info("Login success userId={}", user.getId());
        return issueTokenPair(user, UUID.randomUUID()); // each login starts a NEW family (a new "session lineage")
    }

    /**
     * Refresh = spend + rotate, never re-use.
     * Order of checks matters: signature first (cheap, stateless), then the DB truth.
     *
     * noRollbackFor: the security-critical writes on the failure paths (burning a token
     * family on reuse detection) MUST COMMIT even though the method throws.
     * Default rollback-on-exception would silently undo the burn — stolen tokens survive.
     */
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthResponse refresh(String refreshToken) {
        Claims claims = jwtService.verify(refreshToken, "refresh")
                .orElseThrow(InvalidTokenException::new);

        Long userId = Long.valueOf(claims.getSubject());
        String hash = sha256Hex(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(InvalidTokenException::new); // valid signature but unknown token — e.g. purged
        java.util.UUID familyId = stored.getFamilyId();

        if (stored.isRevoked()) {
            // A spent token came back. Either the client double-fired or the token was
            // stolen and the real user already rotated it. Either way: burn the whole family.
            refreshTokenRepository.revokeAllInFamily(familyId);
            log.error("REFRESH TOKEN REUSE userId={} familyId={} — all tokens in family revoked", userId, familyId);
            throw new InvalidTokenException();
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            spendTokenOrRace(stored, familyId); // mark it, it's dead anyway
            throw new InvalidTokenException();
        }

        spendTokenOrRace(stored, familyId); // atomic compare-and-spend; throws if we lost the race

        // spendToken's bulk update CLEARED the persistence context — re-fetch the user
        // fresh instead of touching the now-detached lazy proxy on the old row.
        User user = userRepository.findById(userId).orElseThrow(InvalidTokenException::new);
        log.info("Token rotated userId={} familyId={}", userId, familyId);
        return issueTokenPair(user, familyId); // same family — the lineage continues
    }

    // One mint point for all three flows: issue the pair AND register the refresh half.
    private AuthResponse issueTokenPair(User user, UUID familyId) {
        String accessToken = jwtService.issueAccessToken(user);
        String refreshToken = jwtService.issueRefreshToken(user, familyId);

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(sha256Hex(refreshToken))
                .familyId(familyId)
                .expiresAt(Instant.now().plusSeconds(jwtService.refreshTtlSeconds()))
                .revoked(false)
                .build());

        return AuthResponse.of(user, accessToken, refreshToken, jwtService.accessTtlSeconds());
    }

    // Returns 0 if another concurrent request spent it first → treat as reuse: burn the family.
    private void spendTokenOrRace(RefreshToken stored, java.util.UUID familyId) {
        if (refreshTokenRepository.spendToken(stored.getTokenHash()) == 0) {
            refreshTokenRepository.revokeAllInFamily(familyId);
            log.error("CONCURRENT REFRESH SPEND familyId={} — family revoked", familyId);
            throw new InvalidTokenException();
        }
    }

    // We store hashes, never raw tokens: a DB leak must not yield API-usable credentials.
    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e); // never happens on a real JVM
        }
    }

    // ONE normalization rule for every entry point — register, login, refresh.
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
