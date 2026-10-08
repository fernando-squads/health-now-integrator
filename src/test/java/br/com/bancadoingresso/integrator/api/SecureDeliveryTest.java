package br.com.bancadoingresso.integrator.api;

import br.com.bancadoingresso.integrator.service.*;
import br.com.bancadoingresso.integrator.extraction.ExtractionOptions;
import com.google.gson.*;
import com.sun.net.httpserver.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@SuppressWarnings("restriction") // Java 8 test server API flagged by legacy Eclipse/JRE access rules.
public class SecureDeliveryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private HttpServer server;
    private HttpTransport http;
    private URI base;
    private LoadArtifact artifact;
    private InstallationKey key;
    private InstallationSessionClient sessions;
    private PublicKey publicKey;
    private boolean authorized, registered, rejectFirstUpload, loseConfirmation, wrongChecksum;
    private int generated, authorizations, uploads, confirms;
    private final String token = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG", receipt = UUID.randomUUID().toString();
    @Before public void setup() throws Exception {
        artifact = DeliveryTest.createArtifact(temporary.getRoot().toPath()); http = new HttpTransport(true);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = http.base("http://127.0.0.1:" + server.getAddress().getPort());
        Path path = temporary.getRoot().toPath().resolve("identity.jceks");
        char[] password = "synthetic-keystore-password".toCharArray();
        key = InstallationKey.open(path, password, artifact.installationId, base);
        publicKey = key.verificationKey();
        sessions = new InstallationSessionClient(key, http);
        server.createContext("/", exchange -> {
            try { handle(exchange); }
            catch (Exception e) { reply(exchange, 500, new JsonObject()); }
            finally {exchange.close();}
        }); server.start();
    }
    @After public void stop() { if (server != null) server.stop(0); }
    private byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(); byte[] bytes = new byte[4096]; int count;
        while((count=input.read(bytes))!=-1) buffer.write(bytes,0,count); return buffer.toByteArray();
    }
    private boolean verify(String message, String signature) throws Exception {
        Signature verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(publicKey);
        verifier.update(message.getBytes(StandardCharsets.UTF_8)); return verifier.verify(Base64.getDecoder().decode(signature));
    }
    private void handle(HttpExchange exchange) throws Exception {
        String path = exchange.getRequestURI().getPath(); byte[] bytes = read(exchange.getRequestBody());
        if (path.startsWith("/s3/")) {
            uploads++;
            if (rejectFirstUpload && uploads==1) {reply(exchange,403,new JsonObject());return;}
            if (!Arrays.equals(bytes, Files.readAllBytes(artifact.path)) || !artifact.checksumBase64().equals(exchange.getRequestHeaders().getFirst("x-amz-checksum-sha256"))
                    || !"*".equals(exchange.getRequestHeaders().getFirst("if-none-match"))
                    || !"AES256".equals(exchange.getRequestHeaders().getFirst("x-amz-server-side-encryption"))) {reply(exchange,400,new JsonObject());return;}
            reply(exchange,200,new JsonObject());return;
        }
        JsonObject body = bytes.length==0 ? new JsonObject() : JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        if (path.endsWith("/activate")) {
            String code = body.get("activation_code").getAsString();
            if (!verify("health-now-activation-v1\n"+artifact.installationId+"\n"+code+"\n"+key.publicKey,body.get("signature").getAsString())) {reply(exchange,401,new JsonObject());return;}
            JsonObject response = new JsonObject();response.addProperty("installation_id",artifact.installationId);response.addProperty("fingerprint",key.fingerprint);
            reply(exchange,200,response);return;
        }
        if (path.endsWith("/tokens")) {
            if (!verify("health-now-session-v1\n"+artifact.installationId+"\n"+key.fingerprint+"\n"+body.get("timestamp").getAsLong()+"\n"+body.get("nonce").getAsString(),body.get("signature").getAsString())) {reply(exchange,401,new JsonObject());return;}
            JsonObject response = new JsonObject(); response.addProperty("access_token",token);response.addProperty("token_type","Bearer");response.addProperty("expires_at",Instant.now().plusSeconds(900).toString());
            reply(exchange,200,response);return;
        }
        if (!("Bearer "+token).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {reply(exchange,401,new JsonObject());return;}
        if (path.endsWith("/upload-authorizations")) {
            authorizations++;authorized=true;
            JsonObject response=DeliveryTest.authorization(artifact,base.toString().replaceAll("/$","")+"/s3");
            if(wrongChecksum) response.getAsJsonObject("required_headers").addProperty("x-amz-checksum-sha256","invalid");
            reply(exchange,200,response);return;
        }
        if (path.endsWith("/availability")) {
            confirms++;registered=true;
            if(loseConfirmation && confirms==1) {reply(exchange,500,new JsonObject());return;}
            reply(exchange,201,DeliveryTest.status(artifact,receipt));return;
        }
        if (path.endsWith("/"+artifact.runId)) {
            JsonObject response=new JsonObject();response.addProperty("run",artifact.runId);response.addProperty("state","Receiving");reply(exchange,200,response);return;
        }
        reply(exchange,authorized?200:404,DeliveryTest.status(artifact,registered?receipt:null));
    }
    private void reply(HttpExchange exchange,int status,JsonObject body) throws IOException {
        byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
        exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);
    }
    private IntegratorService service() {
        return new IntegratorService((o,s)->{generated++;return artifact.path;},new FileAvailabilityAPI(http,sessions),new S3Service(http)::upload);
    }
    private ExtractionOptions options() {return new ExtractionOptions(temporary.getRoot().toPath(),artifact.installationId,LocalDate.now(),10,30,artifact.runId);}
    @Test public void nativeActivationAndSignedHttpUploadUseRequiredHeaders() throws Exception {
        char[] code="synthetic-one-time-code".toCharArray();sessions.activate(code);for(char c:code)assertEquals('\0',c);
        service().integrate(options(),s->{});assertEquals(1,uploads);assertEquals(1,confirms);
    }
    @Test public void expiredCapabilityReauthorizesWithoutGeneratingAnotherArchive() throws Exception {
        rejectFirstUpload=true;service().integrate(options(),s->{});
        assertEquals(1,generated);assertEquals(2,authorizations);assertEquals(1,confirms);
    }
    @Test public void lostAvailabilityResponseRecoversOnRestart() throws Exception {
        loseConfirmation=true;try{service().integrate(options(),s->{});fail();}catch(IOException expected){}
        service().resume(artifact.path,s->{});assertEquals(1,generated);assertEquals(1,uploads);assertEquals(1,confirms);
    }
    @Test public void invalidAuthorizationCannotSendArchive() throws Exception {
        wrongChecksum=true;try{service().integrate(options(),s->{});fail();}catch(IOException expected){}
        assertEquals(0,uploads);assertEquals(0,confirms);
    }
    @Test public void substitutedStatusAndSignedHeadersAreRejected() throws Exception {
        for (String field : Arrays.asList("installation_id", "sha256", "object_key", "size_bytes")) {
            JsonObject response = DeliveryTest.status(artifact, receipt);
            if ("size_bytes".equals(field)) response.addProperty(field, artifact.size + 1);
            else response.addProperty(field, "substituted");
            try { new FileStatus(response, artifact); fail("Substituted status accepted"); }
            catch (IOException expected) { }
        }
        for (String header : Arrays.asList("x-amz-meta-installation-id", "x-amz-checksum-sha256", "content-length")) {
            JsonObject response = DeliveryTest.authorization(artifact, base.toString().replaceAll("/$", "") + "/s3");
            response.getAsJsonObject("required_headers").remove(header);
            try { new UploadAuthorization(response, artifact); fail("Missing signed header accepted"); }
            catch (IOException expected) { }
        }
        JsonObject valid = DeliveryTest.status(artifact, receipt);
        valid.addProperty("expires_at", Instant.now().plusSeconds(300).toString().replace("Z", "+00:00"));
        assertTrue(new FileStatus(valid, artifact).registered());
    }
    @Test public void keystoreIsBoundToApiAndInstallationAndRequiresPassword() throws Exception {
        Path path=temporary.getRoot().toPath().resolve("identity.jceks");
        assertEquals(key.fingerprint,InstallationKey.open(path,"synthetic-keystore-password".toCharArray(),artifact.installationId,base).fingerprint);
        try{InstallationKey.open(path,"wrong-password-with-length".toCharArray(),artifact.installationId,base);fail();}catch(IOException expected){}
        try{InstallationKey.open(path,"synthetic-keystore-password".toCharArray(),UUID.randomUUID().toString(),base);fail();}catch(IOException expected){}
        try{InstallationKey.open(path,"synthetic-keystore-password".toCharArray(),artifact.installationId,URI.create("https://foreign.invalid/"));fail();}catch(IOException expected){}
        assertFalse(new String(Files.readAllBytes(path),StandardCharsets.ISO_8859_1).contains("synthetic-keystore-password"));
    }
    @Test public void productionTransportRejectsHttpAndForeignUploadHosts() throws Exception {
        try{new HttpTransport().base(base.toString());fail();}catch(IOException expected){}
        try{new HttpTransport().upload(artifact.path,artifact.size,URI.create("https://foreign.invalid/upload"),Collections.emptyMap());fail();}catch(IOException expected){}
        assertEquals(0,uploads);
    }
}
