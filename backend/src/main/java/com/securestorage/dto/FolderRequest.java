package com.securestorage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FolderRequest {
    @NotBlank @Size(max = 100)
    private String name;
    private Long parentId;
}
