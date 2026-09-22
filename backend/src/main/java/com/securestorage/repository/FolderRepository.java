package com.securestorage.repository;

import com.securestorage.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByOwnerIdAndParentIdOrderByNameAsc(Long ownerId, Long parentId);
    List<Folder> findByOwnerIdAndParentIdIsNullOrderByNameAsc(Long ownerId);
    Optional<Folder> findByIdAndOwnerId(Long id, Long ownerId);
    boolean existsByOwnerIdAndParentIdAndName(Long ownerId, Long parentId, String name);
    boolean existsByOwnerIdAndParentIdIsNullAndName(Long ownerId, String name);
}
