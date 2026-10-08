package br.com.bancadoingresso.integrator.service;

import br.com.bancadoingresso.integrator.api.*;
import br.com.bancadoingresso.integrator.extraction.*;
import com.google.gson.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.lang.reflect.Proxy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.sync.RequestBody;
import static org.junit.Assert.*;

public class DeliveryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Path archive;
    private int generated, uploads, registrations;
    private boolean uploadFails, registrationFails;
    private final List<String> identities = new ArrayList<String>();

    @Before public void createArtifact() throws Exception {
        LoadFileWriter writer = new LoadFileWriter();
        String fileId = UUID.randomUUID().toString(), runId = UUID.randomUUID().toString();
        Path directory = writer.createRunDirectory(temporary.getRoot().toPath(), runId);
        JsonObject manifest = new JsonObject();
        manifest.addProperty("file_id", fileId);
        manifest.addProperty("run_id", runId);
        manifest.addProperty("installation_id", "synthetic-installation");
        for (String key : Arrays.asList("scope", "extraction_cutoff", "source_version", "mapping_version", "schema_version")) {
            manifest.addProperty(key, "synthetic");
        }
        Path manifestFile = directory.resolve("manifest.json");
        writer.json(manifestFile, manifest);
        Path zip = writer.archive(directory, fileId, Arrays.asList(manifestFile));
        Map<String, Object> metadata = writer.metadata(zip);
        metadata.put("file_id", fileId);
        metadata.put("run_id", runId);
        writer.json(directory.resolve("archive.json"), metadata);
        archive = writer.complete(directory, runId).resolve(zip.getFileName());
    }

    private IntegratorService service(LoadRegistrar registrar) {
        return new IntegratorService((options, status) -> { generated++; return archive; }, new LoadStorage() {
            public void validateConfiguration(String installation) {}
            public String destination(LoadArtifact artifact) { return "s3://synthetic-bucket/" + artifact.fileId; }
            public String objectKey(LoadArtifact artifact) { return artifact.fileId; }
            public void upload(LoadArtifact artifact) throws IOException {
                uploads++;
                if (uploadFails) throw new IOException("Synthetic upload failure");
            }
        }, registrar);
    }

    private LoadRegistrar registrar() {
        return new LoadRegistrar() {
            public void validateConfiguration() {}
            public String destination() { return "synthetic-registration-api"; }
            public String register(LoadArtifact artifact, String key, String idempotency) throws IOException {
                registrations++;
                identities.add(idempotency);
                assertTrue(uploads > 0);
                if (registrationFails) throw new IOException("Synthetic lost registration response");
                return "synthetic-receipt";
            }
        };
    }

    private ExtractionOptions options() {
        return new ExtractionOptions(temporary.getRoot().toPath(), "synthetic-installation", LocalDate.now(), 10, 30);
    }

    @Test public void uploadFailureDoesNotNotifyAndRetryKeepsArchive() throws Exception {
        IntegratorService service = service(registrar());
        uploadFails = true;
        try { service.integrate(options(), s -> {}); fail(); } catch (IOException expected) {}
        assertEquals(0, registrations);
        assertEquals(archive, service.pendingArchive());
        uploadFails = false;
        service.integrate(options(), s -> {});
        assertEquals(1, generated);
        assertEquals(1, registrations);
        assertNull(service.pendingArchive());
    }

    @Test public void lostApiResponseResumesWithSameIdentityAcrossRestart() throws Exception {
        registrationFails = true;
        try { service(registrar()).integrate(options(), s -> {}); fail(); } catch (IOException expected) {}
        registrationFails = false;
        service(registrar()).resume(archive, s -> {});
        assertEquals(1, generated);
        assertEquals(identities.get(0), identities.get(1));
        service(registrar()).resume(archive, s -> {});
        assertEquals(2, registrations);
        assertEquals(2, uploads);
    }

    @Test public void unsupportedApiFailsBeforeGenerationOrUpload() throws Exception {
        try { service(new FileAvailabilityAPI()).integrate(options(), s -> {}); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("endpoint")); }
        assertEquals(0, generated);
        assertEquals(0, uploads);
    }

    @Test public void changedReceiptCannotUpload() throws Exception {
        Path receipt = archive.resolveSibling("archive.json");
        Files.write(receipt, "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try { service(registrar()).resume(archive, s -> {}); fail(); } catch (IOException expected) {}
        assertEquals(0, uploads);
    }

    @Test public void destinationChangeOnRetryIsRejected() throws Exception {
        registrationFails = true;
        try { service(registrar()).integrate(options(), s -> {}); fail(); } catch (IOException expected) {}
        LoadRegistrar other = new LoadRegistrar() {
            public void validateConfiguration() {}
            public String destination() { return "another-api"; }
            public String register(LoadArtifact a, String k, String i) { fail(); return null; }
        };
        try { service(other).resume(archive, s -> {}); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("conflicts")); }
        assertEquals(1, uploads);
    }

    private S3Client client(LoadArtifact artifact, boolean existing, boolean mismatch) {
        return (S3Client) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {S3Client.class},
            (proxy, method, args) -> {
                if ("putObject".equals(method.getName())) {
                    PutObjectRequest request = (PutObjectRequest) args[0];
                    assertEquals("*", request.ifNoneMatch());
                    assertEquals(ServerSideEncryption.AES256, request.serverSideEncryption());
                    assertEquals(artifact.checksumBase64(), request.checksumSHA256());
                    assertEquals("loads/" + artifact.installationId + "/" + artifact.runId + "/" + artifact.fileId + ".zip", request.key());
                    assertEquals(artifact.size, ((RequestBody) args[1]).optionalContentLength().get().longValue());
                    if (existing) throw S3Exception.builder().statusCode(412).message("private-provider-error").build();
                    return PutObjectResponse.builder().build();
                }
                if ("headObject".equals(method.getName())) {
                    assertEquals(ChecksumMode.ENABLED, ((HeadObjectRequest) args[0]).checksumMode());
                    Map<String, String> metadata = new HashMap<String, String>();
                    metadata.put("file-id", artifact.fileId);
                    metadata.put("run-id", artifact.runId);
                    metadata.put("installation-id", artifact.installationId);
                    return HeadObjectResponse.builder().contentLength(artifact.size).metadata(metadata)
                        .checksumSHA256(mismatch ? "invalid" : artifact.checksumBase64()).build();
                }
                throw new AssertionError("Unexpected SDK method: " + method.getName());
            });
    }

    @Test public void officialSdkRequestsUseChecksumEncryptionAndConditionalPut() throws Exception {
        LoadArtifact artifact = LoadArtifact.read(archive);
        new S3Service(client(artifact, false, false), "synthetic-bucket", "loads", "us-east-1").upload(artifact);
    }

    @Test public void existingMatchingS3ObjectIsReconciled() throws Exception {
        LoadArtifact artifact = LoadArtifact.read(archive);
        new S3Service(client(artifact, true, false), "synthetic-bucket", "loads", "us-east-1").upload(artifact);
    }

    @Test public void existingDifferentS3ObjectFails() throws Exception {
        LoadArtifact artifact = LoadArtifact.read(archive);
        try {
            new S3Service(client(artifact, true, true), "synthetic-bucket", "loads", "us-east-1").upload(artifact);
            fail();
        } catch (IOException expected) { assertTrue(expected.getMessage().contains("conflicts")); }
    }
}
