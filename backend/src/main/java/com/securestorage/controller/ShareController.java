package com.securestorage.controller;

import com.securestorage.dto.SharedFolderContentsResponse;
import com.securestorage.entity.SharedLink;
import com.securestorage.service.FileStorageService;
import com.securestorage.service.ShareLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareLinkService shareLinkService;

    @GetMapping("/{token}")
    public ResponseEntity<?> resolve(@PathVariable String token) {
        SharedLink.ShareType type = shareLinkService.peekType(token);
        if (type == SharedLink.ShareType.FOLDER) {
            return ResponseEntity.ok(shareLinkService.listFolderShare(token));
        }
        FileStorageService.LoadedFile loaded = shareLinkService.resolveFileShare(token);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + sanitize(loaded.originalName()) + "\"")
                .contentType(loaded.contentType())
                .body(loaded.resource());
    }

    @GetMapping("/{token}/files/{fileId}")
    public ResponseEntity<Resource> resolveFolderFile(
            @PathVariable String token, @PathVariable Long fileId) {
        FileStorageService.LoadedFile loaded = shareLinkService.resolveFolderFile(token, fileId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + sanitize(loaded.originalName()) + "\"")
                .contentType(loaded.contentType())
                .body(loaded.resource());
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) return "file";
        return name.replace("\"", "").replace("\r", "").replace("\n", "");
    }
}
