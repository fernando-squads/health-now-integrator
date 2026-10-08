package br.com.bancadoingresso.integrator.extraction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Local JSONL/ZIP format v1. No S3 or transport side effects. */
public final class LoadFileWriter {
    private final Gson gson = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    public Path createRunDirectory(Path output, String runId) throws IOException {
        Files.createDirectories(output);
        Path path = output.resolve(runId + ".partial");
        if (Files.getFileStore(output).supportsFileAttributeView("posix")) {
            return Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        }
        return Files.createDirectory(path);
    }

    public BufferedWriter open(Path path) throws IOException {
        if (Files.getFileStore(path.getParent()).supportsFileAttributeView("posix")) {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } else {
            Files.createFile(path);
        }
        return Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.WRITE);
    }

    public void record(BufferedWriter output, ExtractionRecord record) throws IOException {
        Map<String, Object> row = new LinkedHashMap<String, Object>(record.fields());
        row.put("invalid", record.invalid);
        row.put("unmatched", record.unmatched);
        output.write(gson.toJson(row));
        output.write('\n');
    }

    public void json(Path path, Object value) throws IOException {
        try (BufferedWriter output = open(path)) {
            gson.toJson(value, output);
            output.write('\n');
        }
    }

    public Map<String, Object> metadata(Path path) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            int length;
            while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", value & 255));
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("name", path.getFileName().toString());
        metadata.put("size_bytes", Files.size(path));
        metadata.put("sha256", hash.toString());
        return metadata;
    }

    public Path archive(Path directory, String fileId, List<Path> files) throws IOException {
        Path archive = directory.resolve(fileId + ".zip");
        Files.createFile(archive);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive), StandardCharsets.UTF_8)) {
            for (Path file : files) {
                ZipEntry entry = new ZipEntry(file.getFileName().toString());
                entry.setTime(0L);
                output.putNextEntry(entry);
                Files.copy(file, output);
                output.closeEntry();
            }
        }
        if (Files.getFileStore(archive).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(archive, PosixFilePermissions.fromString("r--------"));
        }
        return archive;
    }

    public Path complete(Path partial, String runId) throws IOException {
        Path target = partial.resolveSibling(runId);
        if (Files.exists(target)) throw new FileAlreadyExistsException(target.toString());
        return Files.move(partial, target);
    }
}
