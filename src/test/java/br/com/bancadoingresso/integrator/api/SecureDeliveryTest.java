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
    private InstallationClient sessions;
    private boolean authorized, rejectFirstUpload, loseConfirmation, wrongChecksum;
    private int generated, authorizations, uploads, confirms;
    private final String token = "esb1.abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
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
        char[] code=new char[43];Arrays.fill(code,'a');sessions.open(code);for(char c:code)assertEquals('\0',c);
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
        if(path.endsWith("/bootstrap/activations")) {
            if(used || !body.keySet().equals(Collections.singleton("activation_code")) || body.get("activation_code").getAsString().length()!=43) {reply(exchange,401,new JsonObject());return;}
            used=true;JsonObject response=new JsonObject();response.addProperty("import_id",artifact.runId);response.addProperty("access_token",token);response.addProperty("token_type","Bearer");response.addProperty("expires_at",Instant.now().plusSeconds(86400).toString());
            activationResponse=sign(response,"health-now-bootstrap-token-v1","import_id","access_token","expires_at").deepCopy();
            if(tamperActivation)response.addProperty("expires_at",Instant.now().plusSeconds(60).toString());
            reply(exchange,200,response);return;
        }
        if(revoked || !("Bearer "+token).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {reply(exchange,401,new JsonObject());return;}
        if(path.endsWith("/current")) {
            JsonObject response=new JsonObject();response.addProperty("import_id",artifact.runId);response.addProperty("description","synthetic");response.addProperty("mode","initial");response.addProperty("state","activated");response.addProperty("expires_at",Instant.now().plusSeconds(86400).toString());
            if(tamperCurrent)response.addProperty("import_id",UUID.randomUUID().toString());reply(exchange,200,response);return;
        }
        if (path.endsWith("/upload-authorizations")) {
            authorizations++;authorized=true;
            JsonObject response=DeliveryTest.authorization(artifact,base.toString().replaceAll("/$","")+"/s3");
            if(wrongChecksum) response.getAsJsonObject("required_headers").addProperty("x-amz-checksum-sha256","invalid");
            response.addProperty("import_id",artifact.runId); response.remove("run_id"); response.remove("state"); response.remove("object_key");
            sign(response,"health-now-bootstrap-upload-v1","import_id","file_id","upload_url","expires_at");
            if(tamperUpload)response.addProperty("upload_url","https://foreign.invalid/upload");
            reply(exchange,200,response);return;
        }
        if (path.endsWith("/availability")) {
            confirms++;
            if(loseConfirmation && confirms==1) {
                JsonObject error=new JsonObject();error.addProperty("message","File registration is temporarily unavailable");
                error.add("details",new Gson().toJsonTree(Collections.singletonList("Retry the same file later")));
                reply(exchange,500,error);return;
            }
            JsonObject response=new JsonObject();response.addProperty("import_id",artifact.runId);response.addProperty("description","synthetic");response.addProperty("mode","initial");response.addProperty("state","ready_for_processing");response.addProperty("expires_at",Instant.now().plusSeconds(86400).toString());reply(exchange,201,response);return;
        }
        reply(exchange,authorized?200:404,DeliveryTest.ready(artifact));
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
        sessions.open(new char[0]);assertEquals(artifact.runId,sessions.current().importId);
        service().integrate(options(),s->{});assertEquals(1,uploads);assertEquals(1,confirms);
    }
    @Test public void expiredCapabilityReauthorizesWithoutGeneratingAnotherArchive() throws Exception {
        rejectFirstUpload=true;service().integrate(options(),s->{});
        assertEquals(1,generated);assertEquals(2,authorizations);assertEquals(1,confirms);
    }
    @Test public void lostAvailabilityResponseRecoversOnRestart() throws Exception {
        loseConfirmation=true;try{service().integrate(options(),s->{});fail();}catch(IOException expected){
            assertEquals("A operação HTTP não foi confirmada (status 500): File registration is temporarily unavailable: Retry the same file later",expected.getMessage());
        }
        service().resume(artifact.path,s->{});assertEquals(1,generated);assertEquals(2,uploads);assertEquals(2,confirms);
    }
    @Test public void invalidAuthorizationCannotSendArchive() throws Exception {
        wrongChecksum=true;try{service().integrate(options(),s->{});fail();}catch(IOException expected){}
        assertEquals(0,uploads);assertEquals(0,confirms);
    }
    @Test public void substitutedStatusAndSignedHeadersAreRejected() throws Exception {
        for (String field : Arrays.asList("state", "import_id")) {
            JsonObject response = DeliveryTest.ready(artifact);
            response.addProperty(field, "substituted");
            try { new FileStatus(response, artifact); fail("Substituted status accepted"); }
            catch (IOException expected) { }
        }
        for (String header : Arrays.asList("x-amz-meta-import-id", "x-amz-checksum-sha256", "content-length")) {
            JsonObject response = DeliveryTest.authorization(artifact, base.toString().replaceAll("/$", "") + "/s3");
            response.getAsJsonObject("required_headers").remove(header);
            try { new UploadAuthorization(response, artifact); fail("Missing signed header accepted"); }
            catch (IOException expected) { }
        }
        JsonObject valid = DeliveryTest.ready(artifact);
        valid.addProperty("expires_at", Instant.now().plusSeconds(300).toString().replace("Z", "+00:00"));
        assertTrue(new FileStatus(valid, artifact).registered());
    }
    @Test public void tokenStoreIsOwnerOnlyAndOriginBound() throws Exception {
        assertEquals(token,store.load().get("access_token").getAsString());
        Path path=temporary.getRoot().toPath().resolve("credential.token");
        try{new InstallationTokenStore(path,URI.create("https://foreign.invalid/")).load();fail();}catch(IOException expected){}
        service().integrate(options(),s->{});
        String journal=new String(Files.readAllBytes(artifact.path.resolveSibling("delivery.json")),StandardCharsets.UTF_8);
        assertFalse(journal.contains(token));assertFalse(journal.contains("upload_url"));assertFalse(journal.contains("activation_code"));
    }
    @Test public void tamperedFieldsUnknownKeysAndExpiredTokenFailClosed() throws Exception {
        for(String field:Arrays.asList("import_id","access_token","expires_at","signing_key_id","signature")) {
            JsonObject changed=activationResponse.deepCopy();changed.addProperty(field,"substituted");
            try{verifier.verify(changed,"health-now-bootstrap-token-v1","import_id","access_token","expires_at");fail();}catch(ActivationRequired expected){}
        }
        JsonObject changed=activationResponse.deepCopy();changed.addProperty("protocol_version",1.5);
        try{verifier.verify(changed,"health-now-bootstrap-token-v1","import_id","access_token","expires_at");fail();}catch(ActivationRequired expected){}
        changed=activationResponse.deepCopy();changed.addProperty("expires_at",Instant.now().minusSeconds(1).toString());
        try{store.save(changed);fail();}catch(ActivationRequired expected){}
        assertEquals(token,store.load().get("access_token").getAsString());
        tamperCurrent=true;try{sessions.current();fail();}catch(ActivationRequired expected){}
        assertEquals(0,generated);
    }
    @Test public void revokedTokenAndTamperedUploadRequireActivationWithoutSendingBytes() throws Exception {
        revoked=true;try{sessions.current();fail();}catch(ActivationRequired expected){}
        revoked=false;sessions.open(new char[0]);tamperUpload=true;
        try{service().integrate(options(),s->{});fail();}catch(ActivationRequired expected){}
        assertEquals(0,uploads);assertEquals(0,confirms);
    }
    @Test public void bundledKeyringContainsOnlyUsablePublicKeys() throws Exception { assertNotNull(ApiSignatureVerifier.bundled()); }
    @Test public void invalidActivationSignatureIsNeverPersistedAndCodeIsCleared() throws Exception {
        used=false;tamperActivation=true;
        InstallationTokenStore fresh=new InstallationTokenStore(temporary.getRoot().toPath().resolve("fresh.token"),base);
        InstallationClient client=new InstallationClient(base,http,verifier,fresh);
        char[] code=new char[43];Arrays.fill(code,'a');
        try{client.open(code);fail();}catch(ActivationRequired expected){}
        assertFalse(fresh.exists());for(char c:code)assertEquals('\0',c);
        Arrays.fill(code,'a');
        try{client.open(code);fail();}catch(ActivationRequired expected){}
        assertFalse(fresh.exists());
    }
    @Test public void productionTransportAllowsHttpApiAndRejectsForeignUploadHosts() throws Exception {
        assertEquals(base,new HttpTransport().base(base.toString()));
        try{new HttpTransport().upload(artifact.path,artifact.size,URI.create("https://foreign.invalid/upload"),Collections.emptyMap());fail();}catch(IOException expected){}
        assertEquals(0,uploads);
    }
}
