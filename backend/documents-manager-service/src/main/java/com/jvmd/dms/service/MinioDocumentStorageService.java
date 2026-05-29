package com.jvmd.dms.service;

import com.jvmd.dms.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MinioDocumentStorageService implements DocumentStorageService {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @Override
    public String store(String documentId, String userId, MultipartFile file) {
        try {
            ensureBucket();
            String objectName = objectName(documentId, userId, file.getOriginalFilename());
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(objectName)
                        .contentType(file.getContentType())
                        .stream(inputStream, file.getSize(), -1)
                        .build());
            }
            return objectName;
        } catch (Exception e) {
            throw new IllegalStateException("Could not store document in MinIO.", e);
        }
    }

    @Override
    public StoredObject load(String objectName, String fileName, String contentType) {
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(objectName)
                .build())) {
            return new StoredObject(fileName, contentType, inputStream.readAllBytes());
        } catch (Exception e) {
            throw new IllegalStateException("Could not load document from MinIO.", e);
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Could not delete document from MinIO.", e);
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(properties.getBucket())
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(properties.getBucket())
                    .build());
        }
    }

    private String objectName(String documentId, String userId, String fileName) {
        String safeFileName = fileName == null || fileName.isBlank()
                ? "document"
                : fileName.toLowerCase(Locale.ROOT).replaceAll("[^a-zа-я0-9._-]+", "_");
        return userId + "/" + documentId + "/" + safeFileName;
    }
}
