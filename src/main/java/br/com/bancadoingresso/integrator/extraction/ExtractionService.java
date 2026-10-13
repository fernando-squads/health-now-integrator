package br.com.bancadoingresso.integrator.extraction;

import br.com.bancadoingresso.integrator.persistence.jdbc.*;
import java.io.*;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

public final class ExtractionService {
    public static List<ExtractionRepository> repositories(Connection connection, int timeout) {
        return Arrays.asList(new CitizenJDBC(connection, timeout), new TerritoryJDBC(connection, timeout),
            new FamilyJDBC(connection, timeout), new AcsJDBC(connection, timeout),
            new AttendanceJDBC(connection, timeout), new AttendanceProblemJDBC(connection, timeout),
            new AttendanceExamJDBC(connection, timeout), new AttendanceProcedureJDBC(connection, timeout),
            new AttendanceReferralJDBC(connection, timeout), new ReferenceJDBC(connection, timeout));
    }

    /** The caller owns and closes this dedicated connection. A failed run remains .partial. */
    public Path extract(Connection connection, ExtractionOptions options, Consumer<String> status)
            throws SQLException, IOException {
        if (!connection.getAutoCommit()) throw new SQLException("A fresh extraction connection is required.");
        connection.setReadOnly(true);
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        connection.setAutoCommit(false);
        String runId = options.runId == null ? UUID.randomUUID().toString() : options.runId;
        String fileId = UUID.randomUUID().toString();
        LoadFileWriter writer = new LoadFileWriter();
        Path directory = writer.createRunDirectory(options.output, runId);
        List<Path> files = new ArrayList<Path>();
        List<Map<String, Object>> entities = new ArrayList<Map<String, Object>>();
        Map<String, Object> report = new LinkedHashMap<String, Object>();
        String started = Instant.now().toString();
        try {
            status.accept("Consultando versão do banco PEC");
            String sourceVersion = new SourceVersionJDBC(connection, options.timeoutSeconds).getVersion();
            Map<String, Object> sourceIdentity = new SourceIdentityJDBC(connection, options.timeoutSeconds).read();
            for (ExtractionRepository repository : repositories(connection, options.timeoutSeconds)) {
                for (ExtractionQuery query : repository.queries()) {
                    status.accept("Consultando " + query.entity);
                    long[] totals = repository.count(query, options.cutoff);
                    long extracted = 0, invalid = 0, unmatched = 0, cursor = Long.MIN_VALUE;
                    Path file = directory.resolve(query.entity + ".jsonl");
                    try (BufferedWriter output = writer.open(file)) {
                        while (true) {
                            if (Thread.currentThread().isInterrupted()) throw new IOException("Extraction interrupted.");
                            List<ExtractionRecord> page = repository.page(query, options.cutoff, cursor, options.pageSize);
                            if (page.isEmpty()) break;
                            for (ExtractionRecord record : page) {
                                if (record.sourceId() <= cursor) throw new IOException("Non-unique or unordered extraction key: " + query.entity);
                                writer.record(output, record);
                                cursor = record.sourceId();
                                extracted++;
                                if (record.invalid) invalid++;
                                if (record.unmatched) unmatched++;
                            }
                        }
                    }
                    if (extracted + totals[1] != totals[0]) throw new IOException("Reconciliation failed for " + query.entity);
                    Map<String, Object> counts = new LinkedHashMap<String, Object>();
                    counts.put("source", totals[0]);
                    counts.put("extracted", extracted);
                    counts.put("skipped", totals[1]);
                    counts.put("invalid", invalid);
                    counts.put("unmatched", unmatched);
                    report.put(query.entity, counts);
                    Map<String, Object> entry = writer.metadata(file);
                    entry.put("entity", query.entity);
                    entry.put("source_table", query.sourceTable);
                    entry.put("record_count", extracted);
                    entities.add(entry);
                    files.add(file);
                }
            }
            connection.rollback(); // End the read-only snapshot before compression.
            status.accept("Gerando manifesto e arquivo de carga");
            Path reconciliation = directory.resolve("reconciliation.json");
            writer.json(reconciliation, report);
            files.add(reconciliation);
            Map<String, Object> manifest = new LinkedHashMap<String, Object>();
            manifest.put("file_id", fileId);
            manifest.put("run_id", runId);
            manifest.put("installation_id", options.installationId);
            manifest.put("scope", "full-local-database-reconciliation");
            manifest.put("extraction_cutoff", options.cutoff.toString());
            manifest.put("cutoff_semantics", "inclusive-clinical-date; current snapshot for undated entities");
            manifest.put("snapshot_started_at", started);
            manifest.put("source_version", sourceVersion);
            manifest.put("mapping_version", "esus-local-2");
            manifest.put("schema_version", "local-jsonl-2");
            manifest.put("source_identity", sourceIdentity);
            manifest.put("generated_at", Instant.now().toString());
            manifest.put("files", entities);
            manifest.put("reconciliation", writer.metadata(reconciliation));
            Path manifestFile = directory.resolve("manifest.json");
            writer.json(manifestFile, manifest);
            files.add(manifestFile);
            Path archive = writer.archive(directory, fileId, files);
            Map<String, Object> receipt = writer.metadata(archive);
            receipt.put("file_id", fileId);
            receipt.put("run_id", runId);
            // Archive digest cannot be embedded in its own manifest (circular hash).
            writer.json(directory.resolve("archive.json"), receipt);
            Path completed = writer.complete(directory, runId);
            return completed.resolve(archive.getFileName());
        } finally {
            connection.rollback();
        }
    }
}
