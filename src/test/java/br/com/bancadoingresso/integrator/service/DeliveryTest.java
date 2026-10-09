package br.com.bancadoingresso.integrator.service;
import br.com.bancadoingresso.integrator.api.*;
import br.com.bancadoingresso.integrator.extraction.*;
import com.google.gson.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class DeliveryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private LoadArtifact artifact;
    private int generated, uploads, registrations;
    private boolean failUpload, failConfirm;
    private String destination = "https://synthetic.invalid/";
    private final List<String> identities = new ArrayList<String>();
    @Before public void setup() throws Exception { artifact = createArtifact(temporary.getRoot().toPath()); }
    public static LoadArtifact createArtifact(Path output) throws Exception {
        LoadFileWriter writer = new LoadFileWriter(); String run = UUID.randomUUID().toString(), file = UUID.randomUUID().toString();
        Path dir = writer.createRunDirectory(output, run); JsonObject manifest = new JsonObject();
        manifest.addProperty("run_id", run); manifest.addProperty("file_id", file); manifest.addProperty("installation_id", UUID.randomUUID().toString());
        manifest.addProperty("scope", "synthetic"); manifest.addProperty("extraction_cutoff", "2026-01-01");
        manifest.addProperty("source_version", "5.5.22"); manifest.addProperty("mapping_version", "esus-local-1"); manifest.addProperty("schema_version", "local-jsonl-1");
        Path json = dir.resolve("manifest.json"); writer.json(json, manifest);
        Path zip = writer.archive(dir, file, Arrays.asList(json)); Map<String, Object> metadata = writer.metadata(zip);
        metadata.put("file_id", file); metadata.put("run_id", run); writer.json(dir.resolve("archive.json"), metadata);
        return LoadArtifact.read(writer.complete(dir, run).resolve(zip.getFileName()));
    }
    public static JsonObject authorization(LoadArtifact a, String base) {
        JsonObject response = new JsonObject(), headers = new JsonObject();
        response.addProperty("file_id", a.fileId); response.addProperty("import_id", a.runId);
        response.addProperty("upload_url", base + "/upload?signature=synthetic-capability");
        response.addProperty("expires_at", Instant.now().plusSeconds(300).toString());
        headers.addProperty("content-type", "application/zip"); headers.addProperty("content-length", Long.toString(a.size));
        headers.addProperty("if-none-match", "*"); headers.addProperty("x-amz-checksum-sha256", a.checksumBase64());
        headers.addProperty("x-amz-meta-file-id", a.fileId); headers.addProperty("x-amz-meta-import-id", a.runId);
        headers.addProperty("x-amz-server-side-encryption", "AES256");
        response.add("required_headers", headers); return response;
    }
    public static JsonObject ready(LoadArtifact artifact) {
        JsonObject response = new JsonObject();
        response.addProperty("import_id", artifact.runId); response.addProperty("description", "synthetic");
        response.addProperty("mode", "initial"); response.addProperty("state", "ready_for_processing");
        response.addProperty("expires_at", Instant.now().plusSeconds(300).toString()); return response;
    }
    private IntegratorService service() {
        FileDeliveryAPI api = new FileDeliveryAPI() {
            public String destination() { return destination; }
            public void preflight(String installation, String run) { assertEquals(artifact.runId, run); }
            public UploadAuthorization authorize(LoadArtifact a, String operation, String key) throws IOException {
                assertEquals(64, key.length()); identities.add(operation + key);
                return new UploadAuthorization(authorization(a, "https://synthetic.s3.us-east-1.amazonaws.com"), a);
            }
            public FileStatus confirm(LoadArtifact a, String objectKey, String operation, String key) throws IOException {
                assertTrue(objectKey.isEmpty()); assertEquals(36, operation.length()); assertEquals(64, key.length()); registrations++;
                if (failConfirm) throw new IOException("Synthetic lost response");
                return new FileStatus(ready(a), a);
            }
        };
        return new IntegratorService((o,s) -> {generated++; return artifact.path;}, api, (a,u) -> {uploads++; if(failUpload) throw new IOException("Synthetic upload failure");});
    }
    private ExtractionOptions options() { return new ExtractionOptions(temporary.getRoot().toPath(), artifact.installationId, LocalDate.now(), 10, 30, artifact.runId); }
    @Test public void failedUploadNeverConfirmsAndRetainsArchive() throws Exception {
        IntegratorService service = service(); failUpload = true;
        try {service.integrate(options(),s -> {}); fail();} catch(IOException expected) {}
        assertEquals(0, registrations); assertEquals(artifact.path, service.pendingArchive());
        failUpload = false; service.integrate(options(),s -> {}); assertEquals(1, generated); assertEquals(identities.get(0), identities.get(1));
    }
    @Test public void lostConfirmationResponseRetriesTheSameArchive() throws Exception {
        failConfirm = true;
        try {service().integrate(options(),s -> {}); fail();} catch(IOException expected) {}
        failConfirm = false; service().resume(artifact.path,s -> {});
        assertEquals(1, generated); assertEquals(2, uploads); assertEquals(2, registrations);
    }
    @Test public void expiredAuthorizationRenewsIdentityButNotArchive() throws Exception {
        failUpload = true;
        try {service().integrate(options(),s -> {}); fail();} catch(IOException expected) {}
        failUpload = false; service().resume(artifact.path,s -> {});
        assertEquals(1, generated); assertEquals(identities.get(0), identities.get(1));
    }
    @Test public void destinationCannotChangeOnResume() throws Exception {
        failUpload = true; try {service().integrate(options(),s -> {}); fail();} catch(IOException expected) {}
        failUpload = false; destination = "https://other.invalid/";
        try {service().resume(artifact.path,s -> {}); fail();} catch(IOException expected) {}
        destination = "https://synthetic.invalid/";
        service().resume(artifact.path,s -> {});
        assertEquals(2, uploads);
    }
    @Test public void journalContainsNoCapabilityOrCredential() throws Exception {
        service().integrate(options(),s -> {});
        String journal = new String(Files.readAllBytes(artifact.path.resolveSibling("delivery.json")), java.nio.charset.StandardCharsets.UTF_8);
        for(String forbidden : new String[]{"synthetic-capability", "upload_url", "access_token", "password", "signature", "public_key"}) assertFalse(journal.contains(forbidden));
    }
}
