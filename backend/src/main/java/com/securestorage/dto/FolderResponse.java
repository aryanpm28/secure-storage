package com.securestorage.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class FolderResponse {
    private Long id;
    private String name;
    private Long parentId;
    private LocalDateTime createdAt;
    private int fileCount;
}
