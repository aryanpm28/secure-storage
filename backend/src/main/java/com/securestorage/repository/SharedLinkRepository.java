package com.securestorage.repository;

import com.securestorage.entity.SharedLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SharedLinkRepository extends JpaRepository<SharedLink, Long> {
    Optional<SharedLink> findByToken(String token);
}
