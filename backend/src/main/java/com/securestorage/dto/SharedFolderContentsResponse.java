package com.securestorage.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SharedFolderContentsResponse {
    private String folderName;
    private LocalDateTime expiresAt;
    private List<SharedFolderItem> files;

    @Data
    @Builder
    public static class SharedFolderItem {
        private Long id;
        private String fileName;
        private String category;
        private String mimeType;
        private long fileSize;
        private String downloadUrl;
    }
}
