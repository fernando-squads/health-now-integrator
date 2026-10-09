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
    private KeyPair key;
    private ApiSignatureVerifier verifier;
    private InstallationTokenStore store;
    private JsonObject activationResponse;
    private boolean used, revoked, tamperCurrent, tamperUpload, tamperActivation;
    private final String tokenId=UUID.randomUUID().toString();
    private final char[] password="synthetic-token-password".toCharArray();
    private InstallationClient sessions;
    private boolean authorized, registered, rejectFirstUpload, loseConfirmation, wrongChecksum;
    private int generated, authorizations, uploads, confirms;
    private final String token = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG", receipt = UUID.randomUUID().toString();
    @Before public void setup() throws Exception {
        artifact = DeliveryTest.createArtifact(temporary.getRoot().toPath()); http = new HttpTransport(true);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = http.base("http://127.0.0.1:" + server.getAddress().getPort());
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);key=generator.generateKeyPair();
        verifier=new ApiSignatureVerifier(Collections.singletonMap("synthetic",key.getPublic()));
        store=new InstallationTokenStore(temporary.getRoot().toPath().resolve("credential.token"),base);
        sessions=new InstallationClient(base,http,verifier,store);
        server.createContext("/", exchange -> {
            try { handle(exchange); }
            catch (Exception e) { reply(exchange, 500, new JsonObject()); }
            finally {exchange.close();}
        }); server.start();
        char[] code=new char[43];Arrays.fill(code,'a');sessions.open(code,password);for(char c:code)assertEquals('\0',c);
    }
    @After public void stop() { if (server != null) server.stop(0); }
    private byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(); byte[] bytes = new byte[4096]; int count;
        while((count=input.read(bytes))!=-1) buffer.write(bytes,0,count); return buffer.toByteArray();
    }
    private JsonObject sign(JsonObject response,String kind,String... fields)throws Exception {
        response.addProperty("protocol_version",1);response.addProperty("signing_key_id","synthetic");
        StringBuilder payload=new StringBuilder(kind).append("\n1\nsynthetic");
        for(String field:fields)payload.append('\n').append(response.get(field).getAsString());
        if(response.has("required_headers")) {
            SortedMap<String,String> headers=new TreeMap<String,String>();
            for(Map.Entry<String,JsonElement> h:response.getAsJsonObject("required_headers").entrySet())headers.put(h.getKey(),h.getValue().getAsString());
            payload.append('\n').append(headers.size());for(Map.Entry<String,String> h:headers.entrySet())payload.append('\n').append(h.getKey()).append('\n').append(h.getValue());
        }
        Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(key.getPrivate());signature.update(payload.toString().getBytes(StandardCharsets.UTF_8));
        response.addProperty("signature",Base64.getEncoder().encodeToString(signature.sign()));return response;
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
        if(path.endsWith("/credential-activations")) {
            if(used || !body.keySet().equals(Collections.singleton("activation_code")) || body.get("activation_code").getAsString().length()!=43) {reply(exchange,401,new JsonObject());return;}
            used=true;JsonObject response=new JsonObject();response.addProperty("installation_token_id",tokenId);response.addProperty("installation_token",token);response.addProperty("expires_at",Instant.now().plusSeconds(365L*86400).toString());
            activationResponse=sign(response,"health-now-installation-token-v1","installation_token_id","installation_token","expires_at").deepCopy();
            if(tamperActivation)response.addProperty("expires_at",Instant.now().plusSeconds(60).toString());
            reply(exchange,200,response);return;
        }
        if(revoked || !("Bearer "+token).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {reply(exchange,401,new JsonObject());return;}
        if(path.endsWith("/current")) {
            JsonObject response=new JsonObject();response.addProperty("installation_id",artifact.installationId);response.addProperty("run_id",artifact.runId);response.addProperty("state","Receiving");response.addProperty("source_version","synthetic");response.addProperty("mapping_version","esus-local-1");
            sign(response,"health-now-current-run-v1","installation_id","run_id","state","source_version","mapping_version");if(tamperCurrent)response.addProperty("run_id",UUID.randomUUID().toString());reply(exchange,200,response);return;
        }
        if (path.endsWith("/upload-authorizations")) {
            authorizations++;authorized=true;
            JsonObject response=DeliveryTest.authorization(artifact,base.toString().replaceAll("/$","")+"/s3");
            if(wrongChecksum) response.getAsJsonObject("required_headers").addProperty("x-amz-checksum-sha256","invalid");
            sign(response,"health-now-upload-authorization-v1","file_id","run_id","state","object_key","upload_url","expires_at");
            if(tamperUpload)response.addProperty("upload_url","https://foreign.invalid/upload");
            reply(exchange,200,response);return;
        }
        if (path.endsWith("/availability")) {
            confirms++;registered=true;
            if(loseConfirmation && confirms==1) {reply(exchange,500,new JsonObject());return;}
            reply(exchange,201,DeliveryTest.status(artifact,receipt));return;
        }
        reply(exchange,authorized?200:404,DeliveryTest.status(artifact,registered?receipt:null));
    }
    private void reply(HttpExchange exchange,int status,JsonObject body) throws IOException {
        byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
        exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);
    }
    private IntegratorService service() {
        return new IntegratorService((o,s)->{generated++;return artifact.path;},new FileAvailabilityAPI(sessions),new S3Service(http)::upload);
    }
    private ExtractionOptions options() {return new ExtractionOptions(temporary.getRoot().toPath(),artifact.installationId,LocalDate.now(),10,30,artifact.runId);}
    @Test public void nativeActivationAndSignedHttpUploadUseRequiredHeaders() throws Exception {
        sessions.open(new char[0],password);assertEquals(artifact.runId,sessions.current().run);
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
    @Test public void tokenStoreIsEncryptedOriginBoundAndRequiresPassword() throws Exception {
        assertEquals(token,store.load(password).get("installation_token").getAsString());
        try{store.load("wrong-password-with-length".toCharArray());fail();}catch(IOException expected){}
        Path path=temporary.getRoot().toPath().resolve("credential.token");
        try{new InstallationTokenStore(path,URI.create("https://foreign.invalid/")).load(password);fail();}catch(IOException expected){}
        String stored=new String(Files.readAllBytes(path),StandardCharsets.ISO_8859_1);assertFalse(stored.contains(token));assertFalse(stored.contains(new String(password)));
        service().integrate(options(),s->{});
        String journal=new String(Files.readAllBytes(artifact.path.resolveSibling("delivery.json")),StandardCharsets.UTF_8);
        assertFalse(journal.contains(token));assertFalse(journal.contains(new String(password)));assertFalse(journal.contains("upload_url"));assertFalse(journal.contains("activation_code"));
    }
    @Test public void tamperedFieldsUnknownKeysAndExpiredTokenFailClosed() throws Exception {
        for(String field:Arrays.asList("installation_token_id","installation_token","expires_at","signing_key_id","signature")) {
            JsonObject changed=activationResponse.deepCopy();changed.addProperty(field,"substituted");
            try{verifier.verify(changed,"health-now-installation-token-v1","installation_token_id","installation_token","expires_at");fail();}catch(ActivationRequired expected){}
        }
        JsonObject changed=activationResponse.deepCopy();changed.addProperty("protocol_version",1.5);
        try{verifier.verify(changed,"health-now-installation-token-v1","installation_token_id","installation_token","expires_at");fail();}catch(ActivationRequired expected){}
        changed=activationResponse.deepCopy();changed.addProperty("expires_at",Instant.now().minusSeconds(1).toString());
        try{store.save(changed,password);fail();}catch(ActivationRequired expected){}
        assertEquals(token,store.load(password).get("installation_token").getAsString());
        tamperCurrent=true;try{sessions.current();fail();}catch(ActivationRequired expected){}
        assertEquals(0,generated);
    }
    @Test public void revokedTokenAndTamperedUploadRequireActivationWithoutSendingBytes() throws Exception {
        revoked=true;try{sessions.current();fail();}catch(ActivationRequired expected){}
        revoked=false;sessions.open(new char[0],password);tamperUpload=true;
        try{service().integrate(options(),s->{});fail();}catch(ActivationRequired expected){}
        assertEquals(0,uploads);assertEquals(0,confirms);
    }
    @Test public void bundledKeyringContainsOnlyUsablePublicKeys() throws Exception { assertNotNull(ApiSignatureVerifier.bundled()); }
    @Test public void invalidActivationSignatureIsNeverPersistedAndCodeIsCleared() throws Exception {
        used=false;tamperActivation=true;
        InstallationTokenStore fresh=new InstallationTokenStore(temporary.getRoot().toPath().resolve("fresh.token"),base);
        InstallationClient client=new InstallationClient(base,http,verifier,fresh);
        char[] code=new char[43];Arrays.fill(code,'a');
        try{client.open(code,password);fail();}catch(ActivationRequired expected){}
        assertFalse(fresh.exists());for(char c:code)assertEquals('\0',c);
        Arrays.fill(code,'a');
        try{client.open(code,password);fail();}catch(ActivationRequired expected){}
        assertFalse(fresh.exists());
    }
    @Test public void productionTransportRejectsHttpAndForeignUploadHosts() throws Exception {
        try{new HttpTransport().base(base.toString());fail();}catch(IOException expected){}
        try{new HttpTransport().upload(artifact.path,artifact.size,URI.create("https://foreign.invalid/upload"),Collections.emptyMap());fail();}catch(IOException expected){}
        assertEquals(0,uploads);
    }
}
