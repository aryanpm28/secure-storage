package com.securestorage.controller;

import com.securestorage.dto.*;
import com.securestorage.exception.InvalidTokenException;
import com.securestorage.service.FileStorageService;
import com.securestorage.service.FolderService;
import com.securestorage.service.ShareLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileStorageController {

    private final FileStorageService fileStorageService;
    private final ShareLinkService shareLinkService;
    private final FolderService folderService;

    @PostMapping("/upload")
    public ResponseEntity<FileResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) Long folderId,
            HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (folderId != null) folderService.requireOwnedFolder(folderId, userId);
        return ResponseEntity.ok(fileStorageService.upload(file, userId, folderId));
    }

    @GetMapping
    public ResponseEntity<List<FileResponse>> getMyFiles(
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "root", required = false) Boolean root,
            HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (Boolean.TRUE.equals(root)) {
            return ResponseEntity.ok(fileStorageService.getMyFilesInFolder(userId, null));
        }
        if (folderId != null) {
            return ResponseEntity.ok(fileStorageService.getMyFilesInFolder(userId, folderId));
        }
        return ResponseEntity.ok(fileStorageService.getMyFiles(userId));
    }

    @PostMapping("/{id}/move")
    public ResponseEntity<FileResponse> move(
            @PathVariable Long id,
            @RequestBody MoveFileRequest body,
            HttpServletRequest request) {
        Long userId = currentUserId(request);
        Long folderId = body != null ? body.getFolderId() : null;
        if (folderId != null) folderService.requireOwnedFolder(folderId, userId);
        return ResponseEntity.ok(fileStorageService.moveToFolder(id, userId, folderId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        fileStorageService.delete(id, currentUserId(request));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/usage")
    public ResponseEntity<StorageUsageResponse> getUsage(HttpServletRequest request) {
        return ResponseEntity.ok(fileStorageService.getUsage(currentUserId(request)));
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<ShareLinkResponse> createShareLink(
            @PathVariable Long id,
            @RequestBody(required = false) @Valid ShareLinkRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(shareLinkService.createFileShare(id, currentUserId(request), body));
    }

    @GetMapping("/file/{fileName}")
    public ResponseEntity<Resource> serveFile(@PathVariable String fileName, HttpServletRequest request) {
        FileStorageService.LoadedFile loaded =
                fileStorageService.getFileForOwner(fileName, currentUserId(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + sanitize(loaded.originalName()) + "\"")
                .contentType(loaded.contentType())
                .body(loaded.resource());
    }

    private Long currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("authenticatedUserId");
        if (userId == null) throw new InvalidTokenException("Authentication required");
        return (Long) userId;
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) return "file";
        return name.replace("\"", "").replace("\r", "").replace("\n", "");
    }
}
