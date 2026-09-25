package com.securestorage.service;

import com.securestorage.dto.FileResponse;
import com.securestorage.dto.StorageUsageResponse;
import com.securestorage.entity.StoredFile;
import com.securestorage.entity.StoredFile.FileCategory;
import com.securestorage.exception.*;
import com.securestorage.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final Logger log = Logger.getLogger(FileStorageService.class.getName());

    private final StoredFileRepository storedFileRepository;
    private final S3Client s3Client;

    @Value("${b2.bucket}")
    private String bucket;

    @Value("${storage.max-bytes-per-user:10737418240}")
    private long maxStorageBytesPerUser;

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_DOCUMENT_SIZE = 20L * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 100L * 1024 * 1024;

    private static final List<String> ALLOWED_IMAGE_TYPES =
            List.of("image/jpeg", "image/png", "image/gif", "image/webp");

    private static final List<String> ALLOWED_VIDEO_TYPES =
            List.of("video/mp4", "video/webm", "video/quicktime");

    private static final List<String> ALLOWED_DOCUMENT_TYPES = List.of(
            "application/pdf",
            "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    public record LoadedFile(
            Resource resource,
            MediaType contentType,
            String originalName
    ) {}

    public FileResponse upload(MultipartFile file, Long ownerId, Long folderId) {

        byte[] bytes = readBytes(file);

        FileCategory category = validateAndDetectCategory(file, bytes);

        enforceQuota(ownerId, bytes.length);

        String originalName = file.getOriginalFilename();

        if (originalName != null) {
            originalName = originalName.replace("\\", "/");
            originalName = originalName.substring(originalName.lastIndexOf("/") + 1);
        }

        String extension = safeExtension(
                file.getContentType() != null
                        ? file.getContentType()
                        : "application/octet-stream"
        );

        if (".bin".equals(extension)) {
            extension = switch (category) {
                case IMAGE -> ".jpg";
                case VIDEO -> ".mp4";
                case DOCUMENT -> ".pdf";
            };
        }

        String storedName = UUID.randomUUID() + extension;

        String mime = file.getContentType();

        if (mime == null
                || "application/octet-stream".equals(mime)
                || mime.isBlank()) {

            mime = switch (category) {
                case IMAGE -> "image/jpeg";
                case VIDEO -> "video/mp4";
                case DOCUMENT -> "application/pdf";
            };
        }

        try {

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(storedName)
                    .contentType(mime)
                    .contentLength((long) bytes.length)
                    .build();

            s3Client.putObject(
                    request,
                    RequestBody.fromBytes(bytes)
            );

            StoredFile storedFile = StoredFile.builder()
                    .ownerId(ownerId)
                    .folderId(folderId)
                    .fileName(originalName != null ? originalName : storedName)
                    .filePath(storedName)
                    .category(category)
                    .mimeType(mime)
                    .fileSize(bytes.length)
                    .uploadedAt(LocalDateTime.now())
                    .isPrivate(true)
                    .build();

            storedFileRepository.save(storedFile);

            return toResponse(storedFile);

        } catch (Exception e) {

            log.log(Level.SEVERE, "Failed to upload file to Backblaze B2", e);

            throw new UploadFailedException(
                    "Failed to upload file: " + e.getMessage()
            );
        }
    }

    public List<FileResponse> getMyFiles(Long ownerId) {
        return storedFileRepository
                .findByOwnerIdOrderByUploadedAtDesc(ownerId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<FileResponse> getMyFilesInFolder(Long ownerId, Long folderId) {

        List<StoredFile> files = folderId == null
                ? storedFileRepository
                    .findByOwnerIdAndFolderIdIsNullOrderByUploadedAtDesc(ownerId)
                : storedFileRepository
                    .findByOwnerIdAndFolderIdOrderByUploadedAtDesc(ownerId, folderId);

        return files.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public FileResponse moveToFolder(
            Long fileId,
            Long ownerId,
            Long folderId
    ) {

        StoredFile file = requireOwnedFile(fileId, ownerId);

        file.setFolderId(folderId);

        storedFileRepository.save(file);

        return toResponse(file);
    }

    public void delete(Long fileId, Long ownerId) {

        StoredFile file = storedFileRepository
                .findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() ->
                        new StoredFileNotFoundException(
                                "File not found or you don't own it"
                        )
                );

        try {

            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(file.getFilePath())
                    .build();

            s3Client.deleteObject(request);

        } catch (Exception e) {

            log.log(
                    Level.WARNING,
                    "Could not delete file from Backblaze B2 for id " + fileId,
                    e
            );
        }

        storedFileRepository.delete(file);
    }

    public LoadedFile getFileForOwner(
            String fileName,
            Long requesterId
    ) {

        StoredFile file = storedFileRepository
                .findByFilePath(fileName)
                .orElseThrow(() ->
                        new StoredFileNotFoundException("File not found")
                );

        if (!file.getOwnerId().equals(requesterId)) {
            throw new UnauthorizedAccessException(
                    "You do not have access to this file"
            );
        }

        return loadResource(file);
    }

    public LoadedFile loadFileById(Long fileId) {

        StoredFile file = storedFileRepository
                .findById(fileId)
                .orElseThrow(() ->
                        new StoredFileNotFoundException("File not found")
                );

        return loadResource(file);
    }

    public StoredFile requireOwnedFile(
            Long fileId,
            Long ownerId
    ) {

        return storedFileRepository
                .findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() ->
                        new StoredFileNotFoundException(
                                "File not found or you don't own it"
                        )
                );
    }

    public StorageUsageResponse getUsage(Long ownerId) {

        long used =
                storedFileRepository.sumFileSizeByOwnerId(ownerId);

        double percent =
                maxStorageBytesPerUser == 0
                        ? 0
                        : (used * 100.0) / maxStorageBytesPerUser;

        return StorageUsageResponse.builder()
                .usedBytes(used)
                .maxBytes(maxStorageBytesPerUser)
                .usedPercent(
                        Math.round(percent * 10.0) / 10.0
                )
                .build();
    }

    private LoadedFile loadResource(StoredFile file) {

        try {

            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(file.getFilePath())
                    .build();

            ResponseInputStream<GetObjectResponse> stream =
                    s3Client.getObject(request);

            Resource resource =
                    new InputStreamResource(stream);

            return new LoadedFile(
                    resource,
                    getMediaType(file.getMimeType(), file.getFileName()),
                    file.getFileName()
            );

        } catch (Exception e) {

            log.log(
                    Level.WARNING,
                    "Could not load file from Backblaze B2: "
                            + file.getFilePath(),
                    e
            );

            throw new StoredFileNotFoundException(
                    "File not found"
            );
        }
    }

    private MediaType getMediaType(
            String storedMimeType,
            String fileName
    ) {

        if (storedMimeType != null && !storedMimeType.isBlank()) {

            try {
                return MediaType.parseMediaType(storedMimeType);
            } catch (Exception ignored) {
            }
        }

        String lowerName = fileName.toLowerCase();

        if (lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }

        if (lowerName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }

        if (lowerName.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }

        if (lowerName.endsWith(".webp")) {
            return new MediaType("image", "webp");
        }

        if (lowerName.endsWith(".mp4")) {
            return new MediaType("video", "mp4");
        }

        if (lowerName.endsWith(".webm")) {
            return new MediaType("video", "webm");
        }

        if (lowerName.endsWith(".mov")) {
            return new MediaType("video", "quicktime");
        }

        if (lowerName.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }

        if (lowerName.endsWith(".txt")) {
            return MediaType.TEXT_PLAIN;
        }

        if (lowerName.endsWith(".doc")) {
            return new MediaType("application", "msword");
        }

        if (lowerName.endsWith(".docx")) {
            return new MediaType(
                    "application",
                    "vnd.openxmlformats-officedocument.wordprocessingml.document"
            );
        }

        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private void enforceQuota(
            Long ownerId,
            long incomingBytes
    ) {

        long used =
                storedFileRepository.sumFileSizeByOwnerId(ownerId);

        if (used + incomingBytes > maxStorageBytesPerUser) {

            throw new StorageQuotaExceededException(
                    String.format(
                            "Storage quota exceeded: %.2fGB used of %.0fGB limit",
                            used / (1024.0 * 1024 * 1024),
                            maxStorageBytesPerUser
                                    / (1024.0 * 1024 * 1024)
                    )
            );
        }
    }

    private String safeExtension(String contentType) {

        if (contentType == null) {
            return ".bin";
        }

        return switch (contentType) {

            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";

            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "video/quicktime" -> ".mov";

            case "application/pdf" -> ".pdf";
            case "text/plain" -> ".txt";
            case "application/msword" -> ".doc";

            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    -> ".docx";

            default -> ".bin";
        };
    }

    private byte[] readBytes(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new UploadFailedException(
                    "No file provided"
            );
        }

        try {

            return file.getBytes();

        } catch (IOException e) {

            throw new UploadFailedException(
                    "Could not read uploaded file"
            );
        }
    }

    private FileCategory validateAndDetectCategory(
            MultipartFile file,
            byte[] bytes
    ) {

        String declaredType = file.getContentType();

        MediaType sniffed = sniffType(bytes);

        if (sniffed == null) {

            throw new UnsupportedFileTypeException(
                    "File content does not match a supported format"
            );
        }

        FileCategory category;
        long maxSize;

        if (isImageSignature(sniffed)
                && (declaredType == null
                || ALLOWED_IMAGE_TYPES.contains(declaredType)
                || "application/octet-stream".equals(declaredType))) {

            category = FileCategory.IMAGE;
            maxSize = MAX_IMAGE_SIZE;

        } else if (isVideoSignature(sniffed)
                && (declaredType == null
                || ALLOWED_VIDEO_TYPES.contains(declaredType)
                || "application/octet-stream".equals(declaredType)
                || "video/*".equals(declaredType))) {

            category = FileCategory.VIDEO;
            maxSize = MAX_VIDEO_SIZE;

        } else if (isDocumentSignature(sniffed, declaredType)
                && (declaredType == null
                || ALLOWED_DOCUMENT_TYPES.contains(declaredType)
                || "application/octet-stream".equals(declaredType))) {

            category = FileCategory.DOCUMENT;
            maxSize = MAX_DOCUMENT_SIZE;

        } else if (declaredType != null
                && ALLOWED_IMAGE_TYPES.contains(declaredType)
                && isImageSignature(sniffed)) {

            category = FileCategory.IMAGE;
            maxSize = MAX_IMAGE_SIZE;

        } else if (declaredType != null
                && ALLOWED_VIDEO_TYPES.contains(declaredType)
                && isVideoSignature(sniffed)) {

            category = FileCategory.VIDEO;
            maxSize = MAX_VIDEO_SIZE;

        } else if (declaredType != null
                && ALLOWED_DOCUMENT_TYPES.contains(declaredType)
                && isDocumentSignature(sniffed, declaredType)) {

            category = FileCategory.DOCUMENT;
            maxSize = MAX_DOCUMENT_SIZE;

        } else {

            throw new UnsupportedFileTypeException(
                    "Unsupported file type — allowed: images, videos (MP4/WebM/MOV), documents (PDF/TXT/DOC/DOCX)"
            );
        }

        if (file.getSize() > maxSize) {

            throw new FileSizeExceededException(
                    "File exceeds the "
                            + (maxSize / (1024 * 1024))
                            + "MB limit for "
                            + category.name().toLowerCase()
            );
        }

        return category;
    }

    private boolean isImageSignature(MediaType t) {

        return t.equals(MediaType.IMAGE_JPEG)
                || t.equals(MediaType.IMAGE_PNG)
                || t.equals(MediaType.IMAGE_GIF)
                || "webp".equals(t.getSubtype());
    }

    private boolean isVideoSignature(MediaType t) {

        return "video".equals(t.getType());
    }

    private boolean isDocumentSignature(
            MediaType t,
            String declaredType
    ) {

        if (t.equals(MediaType.APPLICATION_PDF)) {
            return true;
        }

        return t.equals(MediaType.TEXT_PLAIN)
                || "application/msword".equals(declaredType)
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                .equals(declaredType);
    }

    private MediaType sniffType(byte[] bytes) {

        if (bytes.length < 4) {
            return MediaType.TEXT_PLAIN;
        }

        if ((bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8) {

            return MediaType.IMAGE_JPEG;
        }

        if ((bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47) {

            return MediaType.IMAGE_PNG;
        }

        if (bytes[0] == 0x47
                && bytes[1] == 0x49
                && bytes[2] == 0x46) {

            return MediaType.IMAGE_GIF;
        }

        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {

            return new MediaType("image", "webp");
        }

        for (int i = 0;
             i <= Math.min(bytes.length - 8, 56);
             i++) {

            if (bytes[i + 4] == 'f'
                    && bytes[i + 5] == 't'
                    && bytes[i + 6] == 'y'
                    && bytes[i + 7] == 'p') {

                return new MediaType("video", "mp4");
            }
        }

        if (bytes.length >= 4
                && (bytes[0] & 0xFF) == 0x1A
                && (bytes[1] & 0xFF) == 0x45
                && (bytes[2] & 0xFF) == 0xDF
                && (bytes[3] & 0xFF) == 0xA3) {

            return new MediaType("video", "webm");
        }

        if (bytes.length >= 8
                && ((bytes[4] == 'm'
                && bytes[5] == 'o'
                && bytes[6] == 'o'
                && bytes[7] == 'v')
                || (bytes[4] == 'm'
                && bytes[5] == 'd'
                && bytes[6] == 'a'
                && bytes[7] == 't')
                || (bytes[4] == 'w'
                && bytes[5] == 'i'
                && bytes[6] == 'd'
                && bytes[7] == 'e')) {

            return new MediaType("video", "quicktime");
        }

        if (bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F') {

            return MediaType.APPLICATION_PDF;
        }

        if (bytes[0] == 'P'
                && bytes[1] == 'K'
                && bytes[2] == 0x03
                && bytes[3] == 0x04) {

            return new MediaType(
                    "application",
                    "vnd.openxmlformats-officedocument.wordprocessingml.document"
            );
        }

        if ((bytes[0] & 0xFF) == 0xD0
                && (bytes[1] & 0xFF) == 0xCF
                && (bytes[2] & 0xFF) == 0x11
                && (bytes[3] & 0xFF) == 0xE0) {

            return new MediaType(
                    "application",
                    "msword"
            );
        }

        return MediaType.TEXT_PLAIN;
    }

    private FileResponse toResponse(StoredFile file) {

        return FileResponse.builder()
                .id(file.getId())
                .fileName(file.getFileName())
                .url("/files/file/" + file.getFilePath())
                .category(file.getCategory())
                .mimeType(file.getMimeType())
                .fileSize(file.getFileSize())
                .uploadedAt(file.getUploadedAt())
                .build();
    }
}
