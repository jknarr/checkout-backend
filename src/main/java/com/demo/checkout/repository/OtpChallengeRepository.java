package com.demo.checkout.repository;

import com.demo.checkout.domain.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {
}
