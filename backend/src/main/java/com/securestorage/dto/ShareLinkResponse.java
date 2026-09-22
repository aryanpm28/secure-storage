package com.securestorage.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ShareLinkResponse {
    private String token;
    private String shareUrl;
    private LocalDateTime expiresAt;
    private Integer maxDownloads;
}
