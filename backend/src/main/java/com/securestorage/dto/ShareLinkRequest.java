package com.securestorage.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ShareLinkRequest {
    @Min(1)
    private Integer maxDownloads;
}
