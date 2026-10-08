package br.com.bancadoingresso.integrator.service;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/** Official AWS credentials chain; no credentials are persisted by the integrator. */
public final class S3Service implements LoadStorage {
    private final String bucket, prefix, region;
    private final S3Client suppliedClient;

    public S3Service() {
        this(null, System.getenv("AWS_BUCKET"), System.getenv("INTEGRATOR_S3_PREFIX"), System.getenv("AWS_REGION"));
    }

    public S3Service(S3Client client, String bucket, String prefix, String region) {
        this.suppliedClient = client;
        this.bucket = bucket;
        this.prefix = prefix;
        this.region = region;
    }

    @Override public void validateConfiguration(String installationId) throws IOException {
        if (bucket == null || !bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")
                || prefix == null || !prefix.matches("[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*")
                || region == null || region.trim().isEmpty()
                || installationId == null || !installationId.matches("[A-Za-z0-9_-]+")) {
            throw new IOException("Configure AWS_BUCKET, AWS_REGION and INTEGRATOR_S3_PREFIX and a valid installation ID.");
        }
    }

    @Override public String objectKey(LoadArtifact artifact) throws IOException {
        validateConfiguration(artifact.installationId);
        return prefix + "/" + artifact.installationId + "/" + artifact.runId + "/" + artifact.fileId + ".zip";
    }

    @Override public String destination(LoadArtifact artifact) throws IOException {
        return "s3://" + bucket + "/" + objectKey(artifact);
    }

    @Override public void upload(LoadArtifact artifact) throws IOException {
        String key = objectKey(artifact);
        if (artifact.size > 5L * 1024 * 1024 * 1024) throw new IOException("Archive exceeds the single PUT limit (5 GiB).");
        S3Client client = suppliedClient;
        try {
            if (client == null) client = S3Client.builder().region(Region.of(region))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofMinutes(30))
                    .apiCallAttemptTimeout(Duration.ofMinutes(10))).build();
            Map<String, String> metadata = new HashMap<String, String>();
            metadata.put("file-id", artifact.fileId);
            metadata.put("run-id", artifact.runId);
            metadata.put("installation-id", artifact.installationId);
            metadata.put("sha256", artifact.sha256);
            try {
                client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                    .contentType("application/zip").contentLength(artifact.size)
                    .serverSideEncryption(ServerSideEncryption.AES256).metadata(metadata)
                    .checksumSHA256(artifact.checksumBase64()).ifNoneMatch("*").build(),
                    RequestBody.fromFile(artifact.path));
            } catch (S3Exception error) {
                if (error.statusCode() != 412) throw error;
            }
            HeadObjectResponse head = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key)
                .checksumMode(ChecksumMode.ENABLED).build());
            if (head.contentLength() == null || head.contentLength() != artifact.size
                    || !artifact.checksumBase64().equals(head.checksumSHA256())
                    || !artifact.fileId.equals(head.metadata().get("file-id"))
                    || !artifact.runId.equals(head.metadata().get("run-id"))
                    || !artifact.installationId.equals(head.metadata().get("installation-id"))) {
                throw new IOException("S3 object conflicts with the immutable local archive.");
            }
        } catch (SdkException e) {
            throw new IOException("S3 upload could not be confirmed. Verify permissions, region and connectivity; retry the same archive.");
        } finally {
            if (suppliedClient == null && client != null) client.close();
        }
    }
}
