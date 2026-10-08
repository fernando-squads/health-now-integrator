package br.com.bancadoingresso.integrator.persistence.jdbc;

import br.com.bancadoingresso.integrator.extraction.*;
import br.com.bancadoingresso.integrator.persistence.ConnectionFactory;
import br.com.bancadoingresso.integrator.util.DatabaseProperties;
import com.google.gson.*;
import org.junit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

/** Opt-in, read-only verification. Exports stay in the ignored exports/ directory. */
public class ExtractionDatabaseTest {
    @Before public void configure() {
        Assume.assumeTrue(Boolean.getBoolean("esus.integration"));
        DatabaseProperties.creatInstance(System.getProperty("esus.host", "127.0.0.1"),
            System.getProperty("esus.port", "5432"), System.getProperty("esus.database", "esus"),
            System.getProperty("esus.user", System.getProperty("user.name")),
            System.getenv("ESUS_PASSWORD") == null ? "" : System.getenv("ESUS_PASSWORD"));
    }

    @Test public void queriesHaveValidPlansPaginationAndCutoff() throws Exception {
        try (Connection connection = ConnectionFactory.openExtractionConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try (PreparedStatement state = connection.prepareStatement(SqlQueryLoader.load("/queries/verification/transaction-state.sql"));
                 ResultSet result = state.executeQuery()) {
                assertTrue(result.next());
                assertEquals("on", result.getString("read_only"));
                assertEquals("repeatable read", result.getString("isolation"));
            }
            LocalDate cutoff = LocalDate.now();
            for (ExtractionRepository repository : ExtractionService.repositories(connection, 30)) {
                for (ExtractionQuery query : repository.queries()) {
                    try (PreparedStatement plan = connection.prepareStatement("EXPLAIN " + SqlQueryLoader.load(query.resource))) {
                        plan.setDate(1, java.sql.Date.valueOf(cutoff));
                        plan.setLong(2, Long.MIN_VALUE);
                        plan.setInt(3, 1000);
                        plan.setQueryTimeout(30);
                        try (ResultSet result = plan.executeQuery()) { assertTrue(query.entity, result.next()); }
                    }
                    List<ExtractionRecord> first = repository.page(query, cutoff, Long.MIN_VALUE, 2);
                    if (!first.isEmpty()) {
                        long last = first.get(first.size() - 1).sourceId();
                        for (ExtractionRecord row : repository.page(query, cutoff, last, 2)) {
                            assertTrue("Non-advancing cursor: " + query.entity, row.sourceId() > last);
                        }
                    }
                    long[] old = repository.count(query, LocalDate.of(1900, 1, 1));
                    assertTrue(query.entity, old[0] >= old[1]);
                    if ("attendance".equals(query.entity)) assertTrue("Historical cutoff should skip events", old[1] > 0);
                }
            }
            CitizenJDBC citizens = new CitizenJDBC(connection, 30);
            assertNull(citizens.findById(cutoff, Long.MIN_VALUE));
            ExtractionQuery citizenQuery = citizens.queries().get(1);
            List<ExtractionRecord> citizensPage = citizens.page(citizenQuery, cutoff, Long.MIN_VALUE, 1);
            if (!citizensPage.isEmpty()) {
                long id = citizensPage.get(0).sourceId();
                assertEquals(id, citizens.findById(cutoff, id).sourceId());
            }
            assertTrue(connection.isReadOnly());
            connection.rollback();
        }
    }

    @Test public void fullExtractionReconcilesAndProducesVerifiedArchive() throws Exception {
        ExtractionOptions options = ExtractionOptions.fromSystemProperties();
        Path archive;
        String expectedVersion;
        try (Connection connection = ConnectionFactory.openExtractionConnection()) {
            expectedVersion = new SourceVersionJDBC(connection, options.timeoutSeconds).getVersion();
            archive = new ExtractionService().extract(connection, options, message -> {});
        }
        Path directory = archive.getParent();
        LoadFileWriter writer = new LoadFileWriter();
        JsonObject receipt = json(directory.resolve("archive.json"));
        assertEquals(receipt.get("sha256").getAsString(), writer.metadata(archive).get("sha256"));
        assertEquals(receipt.get("size_bytes").getAsLong(), Files.size(archive));
        JsonObject manifest = json(directory.resolve("manifest.json"));
        assertEquals(expectedVersion, manifest.get("source_version").getAsString());
        JsonObject counts = json(directory.resolve("reconciliation.json"));
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            assertEquals(36, zip.size());
            for (JsonElement item : manifest.getAsJsonArray("files")) {
                JsonObject entry = item.getAsJsonObject();
                String name = entry.get("name").getAsString();
                Path file = directory.resolve(name);
                assertNotNull(zip.getEntry(name));
                assertEquals(entry.get("sha256").getAsString(), writer.metadata(file).get("sha256"));
                long lines = 0;
                try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        JsonObject record = JsonParser.parseString(line).getAsJsonObject();
                        assertFalse("Protected field", record.has("no_nome"));
                        for (String identifier : Arrays.asList("cpf", "cns")) {
                            if (record.has(identifier) && !record.get(identifier).isJsonNull()) {
                                assertTrue("Malformed identifier", record.get(identifier).getAsString()
                                    .matches("[0-9]{" + ("cpf".equals(identifier) ? 11 : 15) + "}"));
                            }
                        }
                        lines++;
                    }
                }
                assertEquals(entry.get("record_count").getAsLong(), lines);
                JsonObject entity = counts.getAsJsonObject(entry.get("entity").getAsString());
                assertEquals(entity.get("source").getAsLong(), lines + entity.get("skipped").getAsLong());
            }
        }
        System.out.println("Verified local archive: " + archive.toAbsolutePath());
    }

    private JsonObject json(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
