package com.cinemaseat.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface HoldTokenRepository extends JpaRepository<HoldToken, Long> {
    Optional<HoldToken> findByTokenHash(String tokenHash);

    List<HoldToken> findByBookingIdAndRevokedAtIsNull(Long bookingId);
}