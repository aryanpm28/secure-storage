package com.securestorage.service;

import com.securestorage.dto.ShareLinkRequest;
import com.securestorage.dto.ShareLinkResponse;
import com.securestorage.dto.SharedFolderContentsResponse;
import com.securestorage.entity.Folder;
import com.securestorage.entity.SharedLink;
import com.securestorage.entity.StoredFile;
import com.securestorage.exception.ShareLinkExpiredException;
import com.securestorage.exception.ShareLinkNotFoundException;
import com.securestorage.exception.StoredFileNotFoundException;
import com.securestorage.repository.SharedLinkRepository;
import com.securestorage.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShareLinkService {

    private final SharedLinkRepository sharedLinkRepository;
    private final FileStorageService fileStorageService;
    private final FolderService folderService;
    private final StoredFileRepository storedFileRepository;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private static final int SHARE_EXPIRY_HOURS = 24;
    private static final SecureRandom RANDOM = new SecureRandom();

    public ShareLinkResponse createFileShare(Long fileId, Long ownerId, ShareLinkRequest request) {
        StoredFile file = fileStorageService.requireOwnedFile(fileId, ownerId);
        return saveLink(ownerId, file.getId(), null, SharedLink.ShareType.FILE, request);
    }

    public ShareLinkResponse createFolderShare(Long folderId, Long ownerId, ShareLinkRequest request) {
        Folder folder = folderService.requireOwnedFolder(folderId, ownerId);
        return saveLink(ownerId, null, folder.getId(), SharedLink.ShareType.FOLDER, request);
    }

    private ShareLinkResponse saveLink(Long ownerId, Long fileId, Long folderId,
                                       SharedLink.ShareType type, ShareLinkRequest request) {
        Integer maxDownloads = request != null ? request.getMaxDownloads() : null;
        LocalDateTime now = LocalDateTime.now();
        SharedLink link = SharedLink.builder()
                .storedFileId(fileId).folderId(folderId).ownerId(ownerId)
                .token(generateToken()).shareType(type)
                .createdAt(now).expiresAt(now.plusHours(SHARE_EXPIRY_HOURS))
                .maxDownloads(maxDownloads).downloadCount(0).build();
        sharedLinkRepository.save(link);
        return ShareLinkResponse.builder()
                .token(link.getToken())
                .shareUrl(frontendUrl + "/share/" + link.getToken())
                .expiresAt(link.getExpiresAt())
                .maxDownloads(link.getMaxDownloads())
                .build();
    }

    public SharedLink requireValidLink(String token) {
        if (token == null || token.isBlank()) {
            throw new ShareLinkNotFoundException("This link doesn't exist or was revoked");
        }
        SharedLink link = sharedLinkRepository.findByToken(token)
                .orElseThrow(() -> new ShareLinkNotFoundException("This link doesn't exist or was revoked"));
        if (link.isExpired()) {
            throw new ShareLinkExpiredException("This link has expired (valid for 24 hours only)");
        }
        if (link.isExhausted()) {
            throw new ShareLinkExpiredException("This link has reached its download limit");
        }
        return link;
    }

    public FileStorageService.LoadedFile resolveFileShare(String token) {
        SharedLink link = requireValidLink(token);
        if (link.getShareType() != SharedLink.ShareType.FILE || link.getStoredFileId() == null) {
            throw new ShareLinkNotFoundException("This link points to a folder, not a single file");
        }
        FileStorageService.LoadedFile loaded = fileStorageService.loadFileById(link.getStoredFileId());
        link.setDownloadCount(link.getDownloadCount() + 1);
        sharedLinkRepository.save(link);
        return loaded;
    }

    public SharedFolderContentsResponse listFolderShare(String token) {
        SharedLink link = requireValidLink(token);
        if (link.getShareType() != SharedLink.ShareType.FOLDER || link.getFolderId() == null) {
            throw new ShareLinkNotFoundException("This link points to a file, not a folder");
        }
        Folder folder = folderService.requireOwnedFolder(link.getFolderId(), link.getOwnerId());
        List<StoredFile> files = storedFileRepository.findByFolderIdOrderByUploadedAtDesc(folder.getId());
        List<SharedFolderContentsResponse.SharedFolderItem> items = files.stream()
                .map(f -> SharedFolderContentsResponse.SharedFolderItem.builder()
                        .id(f.getId()).fileName(f.getFileName())
                        .category(f.getCategory().name()).mimeType(f.getMimeType())
                        .fileSize(f.getFileSize())
                        .downloadUrl("/share/" + token + "/files/" + f.getId())
                        .build())
                .collect(Collectors.toList());
        return SharedFolderContentsResponse.builder()
                .folderName(folder.getName()).expiresAt(link.getExpiresAt()).files(items).build();
    }

    public FileStorageService.LoadedFile resolveFolderFile(String token, Long fileId) {
        SharedLink link = requireValidLink(token);
        if (link.getShareType() != SharedLink.ShareType.FOLDER || link.getFolderId() == null) {
            throw new ShareLinkNotFoundException("This link is not a folder share");
        }
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new StoredFileNotFoundException("File not found"));
        if (!link.getFolderId().equals(file.getFolderId())) {
            throw new StoredFileNotFoundException("File is not in this shared folder");
        }
        FileStorageService.LoadedFile loaded = fileStorageService.loadFileById(fileId);
        link.setDownloadCount(link.getDownloadCount() + 1);
        sharedLinkRepository.save(link);
        return loaded;
    }

    public SharedLink.ShareType peekType(String token) {
        return requireValidLink(token).getShareType();
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
