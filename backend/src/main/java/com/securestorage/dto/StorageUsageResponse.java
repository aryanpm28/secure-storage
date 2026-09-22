package com.securestorage.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StorageUsageResponse {
    private long usedBytes;
    private long maxBytes;
    private double usedPercent;
}
