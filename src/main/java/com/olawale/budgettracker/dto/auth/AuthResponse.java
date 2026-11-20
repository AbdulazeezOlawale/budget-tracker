package com.olawale.budgettracker.dto.auth;

import com.olawale.budgettracker.entity.User;

/**
 * THE single authentication contract. register, login and refresh all return
 * exactly this shape — one DTO means they can never drift apart.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresInSeconds,
        UserSummary user) {

    public static AuthResponse of(User user, String accessToken, String refreshToken, long accessTtlSeconds) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                accessTtlSeconds,
                new UserSummary(user.getId(), user.getFullName(), user.getEmail()));
    }

    // Only data the client already has. No hash, ever.
    public record UserSummary(Long id, String fullName, String email) {
    }
}
