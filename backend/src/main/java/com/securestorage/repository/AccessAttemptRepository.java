package com.securestorage.repository;

import com.securestorage.entity.AccessAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

public interface AccessAttemptRepository extends JpaRepository<AccessAttempt, Long> {
    @Query("SELECT COUNT(a) FROM AccessAttempt a WHERE a.ip = :ip AND a.status = 'FAILED' AND a.timestamp > :since")
    long countFailedByIpSince(@Param("ip") String ip, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM AccessAttempt a WHERE a.userId = :userId AND a.status = 'FAILED' AND a.timestamp > :since")
    long countFailedByUserSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
