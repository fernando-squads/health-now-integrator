package br.com.bancadoingresso.integrator.api;

import br.com.bancadoingresso.integrator.util.*;
import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;

/** Only the token and its origin-bound metadata are persisted in an owner-only file. */
public final class InstallationTokenStore {
    private final Path path;
    private final URI api;
    public InstallationTokenStore(Path path, URI api) { this.path = path.toAbsolutePath().normalize(); this.api = api; }
    public static Path defaultPath(URI api) {
        return Paths.get(System.getProperty("user.home"), ".health-now-integrator", Digests.sha256(api.toString().getBytes(StandardCharsets.UTF_8)) + ".token");
    }
    public boolean exists() { return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS); }
    static void validate(JsonObject data, URI api) throws IOException {
        try {
            if (data.get("protocol_version").getAsBigDecimal().intValueExact() != 1
                    || !api.toString().equals(ApiSignatureVerifier.text(data,"api"))
                    || !ApiSignatureVerifier.text(data,"access_token").matches("esb1\\.[A-Za-z0-9_-]{43}")) throw new ActivationRequired();
            ApiSignatureVerifier.uuid(data,"import_id");
            Instant expiry = OffsetDateTime.parse(ApiSignatureVerifier.text(data,"expires_at")).toInstant();
            if (!expiry.isAfter(Instant.now()) || expiry.isAfter(Instant.now().plusSeconds(366L*86400))) throw new ActivationRequired();
        } catch (RuntimeException e) { throw new ActivationRequired(); }
    }
    public JsonObject load() throws IOException { return access(null); }
    public void save(JsonObject response) throws IOException {
        JsonObject data = new JsonObject(); data.addProperty("protocol_version",1); data.addProperty("api",api.toString());
        for (String field : new String[]{"import_id","access_token","expires_at"}) data.addProperty(field,ApiSignatureVerifier.text(response,field));
        validate(data,api); access(data);
    }
    private synchronized JsonObject access(JsonObject data) throws IOException {
        byte[] plain = null;
        try {
            Files.createDirectories(path.getParent()); ProtectedFiles.restrict(path.getParent());
            Path lockPath=path.resolveSibling(path.getFileName()+".lock");
            try (FileChannel lockFile=FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
                 FileLock lock=lockFile.tryLock()) {
                if (lock==null) throw new IOException(); ProtectedFiles.restrict(lockPath);
                if (data==null) {
                    if (!exists() || Files.size(path)>8192) throw new ActivationRequired();
                    ProtectedFiles.restrict(path);
                    plain=Files.readAllBytes(path);
                    JsonObject result=JsonParser.parseString(new String(plain,StandardCharsets.UTF_8)).getAsJsonObject();
                    validate(result,api); return result;
                }
                if (Files.isSymbolicLink(path)) throw new IOException();
                plain=data.toString().getBytes(StandardCharsets.UTF_8);
                Path temporary=Files.createTempFile(path.getParent(),".token-",".pending");
                try {
                    ProtectedFiles.restrict(temporary);
                    try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.WRITE)) {
                        ByteBuffer buffer=ByteBuffer.wrap(plain);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
                    }
                    Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                    ProtectedFiles.restrict(path);
                } finally {Files.deleteIfExists(temporary);}
                return data;
            }
        } catch (ActivationRequired e) { throw e;
        } catch (Exception e) { throw new IOException("Não foi possível abrir ou salvar o token local. Verifique as permissões; se o token foi perdido, solicite nova ativação.");
        } finally { if(plain!=null)java.util.Arrays.fill(plain,(byte)0); }
    }
}
