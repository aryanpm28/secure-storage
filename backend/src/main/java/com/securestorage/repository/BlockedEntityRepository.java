package com.securestorage.repository;

import com.securestorage.entity.BlockedEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;

public interface BlockedEntityRepository extends JpaRepository<BlockedEntity, Long> {
    @Query("SELECT b FROM BlockedEntity b WHERE b.ipOrUser = :ipOrUser AND b.expiresAt > :now")
    Optional<BlockedEntity> findActiveBlock(@Param("ipOrUser") String ipOrUser, @Param("now") LocalDateTime now);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM BlockedEntity b WHERE b.ipOrUser = :ipOrUser AND b.expiresAt > :now")
    boolean existsActiveBlock(@Param("ipOrUser") String ipOrUser, @Param("now") LocalDateTime now);
}
