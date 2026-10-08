package br.com.bancadoingresso.integrator.service;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Connection;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.function.Consumer;
import br.com.bancadoingresso.integrator.api.FileAvailabilityAPI;
import br.com.bancadoingresso.integrator.api.LoadRegistrar;
import br.com.bancadoingresso.integrator.extraction.ExtractionOptions;
import br.com.bancadoingresso.integrator.extraction.ExtractionService;
import br.com.bancadoingresso.integrator.persistence.ConnectionFactory;

public final class IntegratorService {
    @FunctionalInterface public interface Generator {
        Path generate(ExtractionOptions options, Consumer<String> status) throws SQLException, IOException;
    }

    private final Generator generator;
    private final LoadStorage storage;
    private final LoadRegistrar registrar;
    private Path pendingArchive;

    public IntegratorService() {
        this((options, status) -> {
            status.accept("Testando conexão");
            try (Connection connection = ConnectionFactory.openExtractionConnection()) {
                if (!connection.isValid(5)) throw new SQLException("Database connection could not be validated.");
                return new ExtractionService().extract(connection, options, status);
            }
        }, new S3Service(), new FileAvailabilityAPI());
    }

    public IntegratorService(Generator generator, LoadStorage storage, LoadRegistrar registrar) {
        this.generator = java.util.Objects.requireNonNull(generator);
        this.storage = java.util.Objects.requireNonNull(storage);
        this.registrar = java.util.Objects.requireNonNull(registrar);
    }

    public void integrate() throws SQLException, IOException {
        integrate(ExtractionOptions.fromSystemProperties(), message -> {});
    }

    /** Reuses the same generated archive when this instance is retried after a delivery failure. */
    public synchronized Path integrate(ExtractionOptions options, Consumer<String> status) throws SQLException, IOException {
        registrar.validateConfiguration();
        storage.validateConfiguration(options.installationId);
        if (pendingArchive == null) pendingArchive = generator.generate(options, status);
        LoadArtifact artifact = LoadArtifact.read(pendingArchive);
        if (!options.installationId.equals(artifact.installationId)) throw new IOException("Archive belongs to another installation.");
        deliver(artifact, status);
        Path completed = pendingArchive;
        pendingArchive = null;
        return completed;
    }

    /** Resume across process restarts without querying the source or changing file/run identity. */
    public synchronized Path resume(Path archive, Consumer<String> status) throws IOException {
        registrar.validateConfiguration();
        pendingArchive = archive;
        LoadArtifact artifact = LoadArtifact.read(archive);
        storage.validateConfiguration(artifact.installationId);
        deliver(artifact, status);
        pendingArchive = null;
        return artifact.path;
    }

    public synchronized Path pendingArchive() { return pendingArchive; }

    private void deliver(LoadArtifact artifact, Consumer<String> status) throws IOException {
        try (FileChannel channel = FileChannel.open(artifact.path.resolveSibling("delivery.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) throw new IOException("This archive is already being delivered.");
            DeliveryJournal journal = new DeliveryJournal(artifact, storage.destination(artifact), registrar.destination());
            if ("registered".equals(journal.state())) {
                status.accept("Arquivo já registrado na API; aguardando processamento");
                return;
            }
            status.accept("Enviando e verificando arquivo no S3");
            // Also reconcile an uploaded object on retry; conditional PUT never overwrites it.
            storage.upload(artifact);
            journal.save("uploaded", null);
            status.accept("Informando disponibilidade do arquivo à API");
            String receipt = registrar.register(artifact, storage.objectKey(artifact), artifact.idempotencyKey());
            if (receipt == null || !receipt.matches("[A-Za-z0-9_-]{1,128}")) {
                throw new IOException("API registration was not confirmed. Retry the same archive.");
            }
            journal.save("registered", receipt);
            status.accept("Arquivo registrado na API; aguardando processamento");
        } catch (OverlappingFileLockException e) {
            throw new IOException("This archive is already being delivered.");
        }
    }
}
