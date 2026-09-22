package com.securestorage.dto;

import com.securestorage.entity.StoredFile.FileCategory;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class FileResponse {
    private Long id;
    private String fileName;
    private String url;
    private FileCategory category;
    private String mimeType;
    private long fileSize;
    private LocalDateTime uploadedAt;
}
