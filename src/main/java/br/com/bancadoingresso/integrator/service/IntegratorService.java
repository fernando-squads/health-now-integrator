package br.com.bancadoingresso.integrator.service;

import br.com.bancadoingresso.integrator.api.*;
import br.com.bancadoingresso.integrator.extraction.*;
import br.com.bancadoingresso.integrator.persistence.ConnectionFactory;
import java.io.IOException;
import java.sql.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.function.Consumer;

public final class IntegratorService {
    @FunctionalInterface public interface Generator { Path generate(ExtractionOptions options, Consumer<String> status) throws SQLException, IOException; }
    @FunctionalInterface public interface Uploader { void upload(LoadArtifact artifact, UploadAuthorization authorization) throws IOException; }
    private final Generator generator;
    private final FileDeliveryAPI api;
    private final Uploader uploader;
    private Path pendingArchive;
    public IntegratorService(FileDeliveryAPI api, HttpTransport http) {
        this((options, status) -> {
            status.accept("Testando conexão");
            try (Connection connection = ConnectionFactory.openExtractionConnection()) {
                if (!connection.isValid(5)) throw new SQLException("Conexão com o banco não confirmada.");
                return new ExtractionService().extract(connection, options, status);
            }
        }, api, new S3Service(http)::upload);
    }
    public IntegratorService(Generator generator, FileDeliveryAPI api, Uploader uploader) {
        this.generator = java.util.Objects.requireNonNull(generator); this.api = java.util.Objects.requireNonNull(api);
        this.uploader = java.util.Objects.requireNonNull(uploader);
    }
    public synchronized Path integrate(ExtractionOptions options, Consumer<String> status) throws SQLException, IOException {
        if (options.runId == null) throw new IOException("A API não retornou uma execução autorizada.");
        api.preflight(options.installationId, options.runId);
        if (pendingArchive == null) pendingArchive = generator.generate(options, status);
        LoadArtifact artifact = LoadArtifact.read(pendingArchive);
        if (!options.installationId.equals(artifact.installationId) || !options.runId.equals(artifact.runId)) throw new IOException("Arquivo pertence a outra instalação ou execução.");
        deliver(artifact, status); Path result = pendingArchive; pendingArchive = null; return result;
    }
    public synchronized Path resume(Path archive, Consumer<String> status) throws IOException {
        pendingArchive = archive; LoadArtifact artifact = LoadArtifact.read(archive);
        deliver(artifact, status); pendingArchive = null; return artifact.path;
    }
    public synchronized Path pendingArchive() { return pendingArchive; }
    private void deliver(LoadArtifact artifact, Consumer<String> status) throws IOException {
        try (FileChannel channel = FileChannel.open(artifact.path.resolveSibling("delivery.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Arquivo já está sendo enviado.");
            DeliveryJournal journal = new DeliveryJournal(artifact, api.destination());
            for (int attempt = 0; attempt < 2; attempt++) {
                FileStatus current = api.status(artifact);
                if (current != null) {
                    journal.pin(current.objectKey);
                    if (current.registered()) { complete(journal, current, status); return; }
                    if ("failed".equals(current.state)) throw new IOException("A API informou falha de processamento. Não gere outra carga sem revisão.");
                    if (!current.expires.isAfter(Instant.now())) journal.renew();
                }
                status.accept("Solicitando autorização temporária de upload");
                UploadAuthorization authorization = api.authorize(artifact, journal.operation("authorize"), journal.key("authorize"));
                journal.pin(authorization.objectKey);
                status.accept("Enviando arquivo ao S3");
                try { uploader.upload(artifact, authorization); }
                catch (HttpTransport.Failure failure) {
                    if (failure.status == 403 && attempt == 0) continue;
                    throw failure;
                }
                journal.save("uploaded", null);
                status.accept("Validando e registrando arquivo na API");
                try {
                    FileStatus receipt = api.confirm(artifact, authorization.objectKey, journal.operation("confirm"), journal.key("confirm"));
                    if (!receipt.registered() || !receipt.objectKey.equals(authorization.objectKey)) throw new IOException("Registro não confirmado.");
                    complete(journal, receipt, status); return;
                } catch (HttpTransport.Failure failure) {
                    if (failure.status == 409 && attempt == 0 && !authorization.expiresAt.isAfter(Instant.now())) continue;
                    throw failure;
                }
            }
            throw new IOException("Não foi possível renovar a autorização. Preserve o arquivo e tente novamente.");
        } catch (OverlappingFileLockException e) { throw new IOException("Arquivo já está sendo enviado."); }
    }
    private void complete(DeliveryJournal journal, FileStatus result, Consumer<String> status) throws IOException {
        journal.save("registered", result.receipt);
        status.accept("Arquivo registrado na API; processamento e publicação são etapas posteriores");
    }
}
