package com.securestorage.service;

import com.securestorage.dto.FolderRequest;
import com.securestorage.dto.FolderResponse;
import com.securestorage.entity.Folder;
import com.securestorage.entity.StoredFile;
import com.securestorage.exception.StoredFileNotFoundException;
import com.securestorage.exception.UploadFailedException;
import com.securestorage.repository.FolderRepository;
import com.securestorage.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final StoredFileRepository storedFileRepository;

    public FolderResponse create(Long ownerId, FolderRequest request) {
        String name = request.getName().trim();
        Long parentId = request.getParentId();
        if (parentId != null) {
            folderRepository.findByIdAndOwnerId(parentId, ownerId)
                    .orElseThrow(() -> new StoredFileNotFoundException("Parent folder not found"));
            if (folderRepository.existsByOwnerIdAndParentIdAndName(ownerId, parentId, name)) {
                throw new UploadFailedException("A folder with that name already exists here");
            }
        } else if (folderRepository.existsByOwnerIdAndParentIdIsNullAndName(ownerId, name)) {
            throw new UploadFailedException("A folder with that name already exists here");
        }
        Folder folder = Folder.builder()
                .ownerId(ownerId).name(name).parentId(parentId)
                .createdAt(LocalDateTime.now()).build();
        folderRepository.save(folder);
        return toResponse(folder);
    }

    public List<FolderResponse> list(Long ownerId, Long parentId) {
        List<Folder> folders = parentId == null
                ? folderRepository.findByOwnerIdAndParentIdIsNullOrderByNameAsc(ownerId)
                : folderRepository.findByOwnerIdAndParentIdOrderByNameAsc(ownerId, parentId);
        return folders.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public void delete(Long folderId, Long ownerId) {
        Folder folder = folderRepository.findByIdAndOwnerId(folderId, ownerId)
                .orElseThrow(() -> new StoredFileNotFoundException("Folder not found"));
        for (StoredFile f : storedFileRepository.findByFolderIdOrderByUploadedAtDesc(folderId)) {
            f.setFolderId(null);
            storedFileRepository.save(f);
        }
        for (Folder child : folderRepository.findByOwnerIdAndParentIdOrderByNameAsc(ownerId, folderId)) {
            child.setParentId(null);
            folderRepository.save(child);
        }
        folderRepository.delete(folder);
    }

    public Folder requireOwnedFolder(Long folderId, Long ownerId) {
        return folderRepository.findByIdAndOwnerId(folderId, ownerId)
                .orElseThrow(() -> new StoredFileNotFoundException("Folder not found or you don't own it"));
    }

    private FolderResponse toResponse(Folder folder) {
        int count = storedFileRepository.findByFolderIdOrderByUploadedAtDesc(folder.getId()).size();
        return FolderResponse.builder()
                .id(folder.getId()).name(folder.getName()).parentId(folder.getParentId())
                .createdAt(folder.getCreatedAt()).fileCount(count).build();
    }
}
