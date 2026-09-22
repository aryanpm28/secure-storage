package com.securestorage.controller;

import com.securestorage.dto.FolderRequest;
import com.securestorage.dto.FolderResponse;
import com.securestorage.dto.ShareLinkRequest;
import com.securestorage.dto.ShareLinkResponse;
import com.securestorage.exception.InvalidTokenException;
import com.securestorage.service.FolderService;
import com.securestorage.service.ShareLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;
    private final ShareLinkService shareLinkService;

    @PostMapping
    public ResponseEntity<FolderResponse> create(@Valid @RequestBody FolderRequest request,
                                                 HttpServletRequest http) {
        return ResponseEntity.ok(folderService.create(currentUserId(http), request));
    }

    @GetMapping
    public ResponseEntity<List<FolderResponse>> list(
            @RequestParam(required = false) Long parentId,
            HttpServletRequest http) {
        return ResponseEntity.ok(folderService.list(currentUserId(http), parentId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        folderService.delete(id, currentUserId(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<ShareLinkResponse> share(
            @PathVariable Long id,
            @RequestBody(required = false) @Valid ShareLinkRequest body,
            HttpServletRequest http) {
        return ResponseEntity.ok(shareLinkService.createFolderShare(id, currentUserId(http), body));
    }

    private Long currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("authenticatedUserId");
        if (userId == null) throw new InvalidTokenException("Authentication required");
        return (Long) userId;
    }
}
