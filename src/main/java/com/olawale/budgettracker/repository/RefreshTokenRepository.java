package com.olawale.budgettracker.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.olawale.budgettracker.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Atomic compare-and-spend: flips revoked ONLY if the row is still unspent.
     * Returns 1 if this caller won the token, 0 if someone else spent it first.
     * This is what closes the concurrent double-spend race a plain find-then-save would leave open.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.tokenHash = :tokenHash AND t.revoked = false")
    int spendToken(@Param("tokenHash") String tokenHash);

    /**
     * Reuse detection: burns an ENTIRE token family. When a spent token is replayed,
     * every live descendant is revoked too — the attacker's refreshed tokens die with it.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.familyId = :familyId AND t.revoked = false")
    int revokeAllInFamily(@Param("familyId") UUID familyId);
}
