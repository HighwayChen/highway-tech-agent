package com.highway.agent.common.service;

import com.highway.agent.common.config.MinioConfig;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.PutObjectArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    private final MinioConfig minioConfig;

    @PostConstruct
    public void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
                log.info("Created MinIO bucket: {}", minioConfig.getBucket());
            }
        } catch (Exception e) {
            log.warn("Failed to ensure MinIO bucket '{}': {}", minioConfig.getBucket(), e.getMessage());
        }
    }

    public void putObject(String key, String content) {
        try {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .object(key)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .contentType(guessContentType(key))
                            .build());
            log.debug("Uploaded object to MinIO: {}", key);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload object to MinIO: " + key, e);
        }
    }

    public void putObject(String key, InputStream inputStream, long size, String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .object(key)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build());
            log.debug("Uploaded binary object to MinIO: {}", key);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload object to MinIO: " + key, e);
        }
    }

    public String getObject(String key) {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .object(key)
                        .build())) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get object from MinIO: " + key, e);
        }
    }

    public byte[] getObjectBytes(String key) {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .object(key)
                        .build());
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            stream.transferTo(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get object from MinIO: " + key, e);
        }
    }

    private String guessContentType(String key) {
        if (key.endsWith(".json")) return "application/json";
        if (key.endsWith(".md")) return "text/markdown";
        return "application/octet-stream";
    }
}
