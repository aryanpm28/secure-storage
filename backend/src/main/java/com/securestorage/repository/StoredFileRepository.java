package com.securestorage.repository;

import com.securestorage.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
    List<StoredFile> findByOwnerIdOrderByUploadedAtDesc(Long ownerId);
    List<StoredFile> findByOwnerIdAndFolderIdOrderByUploadedAtDesc(Long ownerId, Long folderId);
    List<StoredFile> findByOwnerIdAndFolderIdIsNullOrderByUploadedAtDesc(Long ownerId);
    List<StoredFile> findByFolderIdOrderByUploadedAtDesc(Long folderId);
    Optional<StoredFile> findByIdAndOwnerId(Long id, Long ownerId);
    Optional<StoredFile> findByFilePath(String filePath);

    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM StoredFile f WHERE f.ownerId = :ownerId")
    long sumFileSizeByOwnerId(@Param("ownerId") Long ownerId);
}
