package br.com.bancadoingresso.integrator.persistence.jdbc;

import br.com.bancadoingresso.integrator.extraction.*;
import com.google.gson.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

public class ExtractionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void patientContactAndAddressProjectionPreservesSourceValuesInJsonl() throws Exception {
        String sql = SqlQueryLoader.load("/queries/registration/find-page.sql");
        assertTrue(sql.contains("citizen.nu_telefone_residencial"));
        assertTrue(sql.contains("citizen.nu_telefone_celular"));
        assertTrue(sql.contains("citizen.nu_telefone_contato"));
        assertTrue(sql.contains("citizen.ds_email AS no_email"));
        assertTrue(sql.contains("locality.no_localidade"));
        assertTrue(sql.contains("state.no_uf"));
        final List<String> fields = Arrays.asList("nu_telefone_residencial", "nu_telefone_celular",
            "nu_telefone_contato", "no_email", "ds_cep", "ds_logradouro", "no_bairro",
            "nu_numero", "no_localidade", "no_uf");
        for (String value : Arrays.asList("0000000000", null, "", "invalid@example")) {
            final String contact = value;
            ResultSetMetaData metadata = (ResultSetMetaData) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ResultSetMetaData.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getColumnCount": return fields.size() + 1;
                        case "getColumnLabel": return ((Integer)args[0]) == 1 ? "source_id" : fields.get((Integer)args[0] - 2);
                        case "getColumnType": return Types.VARCHAR;
                        case "getColumnTypeName": return "varchar";
                        default: return null;
                    }
                });
            ResultSet result = (ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getMetaData": return metadata;
                        case "getObject": return ((Integer)args[0]) == 1 ? 1L : contact;
                        case "getBoolean": return false;
                        default: return null;
                    }
                });
            ExtractionRecord record = new ReferenceJDBC(null, 5).read(result);
            StringWriter text = new StringWriter();
            try (BufferedWriter output = new BufferedWriter(text)) { new LoadFileWriter().record(output, record); }
            JsonObject stored = JsonParser.parseString(text.toString()).getAsJsonObject();
            for (String field : fields) {
                JsonElement storedValue = stored.get(field);
                assertTrue(contact == null ? storedValue.isJsonNull() : contact.equals(storedValue.getAsString()));
            }
        }
    }

    @Test public void allResourcesLoadAndAreExplicitProjections() throws Exception {
        Set<String> entities = new HashSet<String>();
        for (ExtractionRepository repository : ExtractionService.repositories(null, 30)) {
            for (ExtractionQuery query : repository.queries()) {
                assertTrue("Duplicate entity", entities.add(query.entity));
                String page = SqlQueryLoader.load(query.resource);
                assertFalse(page.toLowerCase(Locale.ROOT).contains("select *"));
                assertFalse(page.contains("s.no_nome"));
                assertTrue(page.contains("source_id > ?"));
                assertTrue(page.contains("LIMIT ?"));
                assertTrue(SqlQueryLoader.load(query.resource.replace("find-page", "count")).contains("skipped_count"));
            }
        }
        assertEquals(35, entities.size());
        assertTrue(SqlQueryLoader.load("/queries/citizen/find-by-id.sql").contains("source_id = ?"));
    }

    @Test public void missingResourceFailsClearly() throws Exception {
        try {
            SqlQueryLoader.load("/queries/missing.sql");
            fail("Missing query must fail");
        } catch (SQLException expected) { assertTrue(expected.getMessage().contains("not found")); }
    }

    @Test public void writerPreservesUtf8NullsChecksumsAndDoesNotOverwrite() throws Exception {
        LoadFileWriter writer = new LoadFileWriter();
        Path directory = writer.createRunDirectory(temporary.getRoot().toPath(), "synthetic");
        Path file = directory.resolve("citizen.jsonl");
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("source_id", 1L);
        fields.put("name", "Cidadão sintético");
        fields.put("cpf", null);
        try (BufferedWriter output = writer.open(file)) { writer.record(output, new ExtractionRecord(fields, true, true)); }
        JsonObject row = JsonParser.parseString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("Cidadão sintético", row.get("name").getAsString());
        assertTrue(row.get("cpf").isJsonNull());
        assertTrue(row.get("unmatched").getAsBoolean());
        assertEquals(64, writer.metadata(file).get("sha256").toString().length());
        Path zip = writer.archive(directory, "synthetic-file", Arrays.asList(file));
        try (ZipFile archive = new ZipFile(zip.toFile())) { assertEquals(1, archive.size()); }
        try { writer.open(file); fail("Overwrite must fail"); }
        catch (FileAlreadyExistsException expected) { /* Immutable output. */ }
        assertTrue(Files.isDirectory(writer.complete(directory, "synthetic")));
    }

    @Test public void writerPreservesFailedAttemptBeforeRetryingSameRun() throws Exception {
        LoadFileWriter writer = new LoadFileWriter();
        Path first = writer.createRunDirectory(temporary.getRoot().toPath(), "synthetic");
        Path retry = writer.createRunDirectory(temporary.getRoot().toPath(), "synthetic");

        assertEquals("synthetic.partial", first.getFileName().toString());
        assertEquals("synthetic-retry-1.partial", retry.getFileName().toString());
        assertTrue(Files.isDirectory(first));
        assertTrue(Files.isDirectory(writer.complete(retry, "synthetic")));
        assertTrue(Files.isDirectory(first));
    }

    @Test public void invalidConfigurationIsRejected() {
        try {
            new ExtractionOptions(Paths.get("exports"), "test", LocalDate.now(), 0, 30);
            fail("Zero page size must fail");
        } catch (IllegalArgumentException expected) { /* Expected. */ }
    }

    @Test public void databaseFailureIsSanitizedAndCannotPublishAnArchive() throws Exception {
        final boolean[] autoCommit = {true};
        Connection connection = (Connection) java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getAutoCommit": return autoCommit[0];
                    case "setAutoCommit": autoCommit[0] = (Boolean) args[0]; return null;
                    case "isReadOnly": return true;
                    case "getTransactionIsolation": return Connection.TRANSACTION_REPEATABLE_READ;
                    case "prepareStatement": throw new SQLException("secret-source-value", "42501");
                    default: return null;
                }
            });
        Path output = temporary.getRoot().toPath();
        try {
            new ExtractionService().extract(connection,
                new ExtractionOptions(output, "synthetic", LocalDate.now(), 2, 30), message -> {});
            fail("Query failure must abort the run");
        } catch (SQLException expected) {
            assertFalse(expected.toString().contains("secret-source-value"));
            assertNull(expected.getCause());
            assertEquals("42501", expected.getSQLState());
        }
        try (java.util.stream.Stream<Path> paths = Files.list(output)) {
            Path partial = paths.findFirst().get();
            assertTrue(partial.getFileName().toString().endsWith(".partial"));
            assertFalse(Files.exists(partial.resolve("manifest.json")));
            assertFalse(Files.exists(partial.resolve("archive.json")));
        }
    }
}
