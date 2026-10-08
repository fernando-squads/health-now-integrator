package br.com.bancadoingresso.integrator.extraction;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

public final class ExtractionOptions {
    public final Path output;
    public final String installationId;
    public final String runId;
    public final LocalDate cutoff;
    public final int pageSize;
    public final int timeoutSeconds;

    public ExtractionOptions(Path output, String installationId,
                             LocalDate cutoff, int pageSize, int timeoutSeconds) {
        this(output, installationId, cutoff, pageSize, timeoutSeconds, null);
    }
    public ExtractionOptions(Path output, String installationId,
                             LocalDate cutoff, int pageSize, int timeoutSeconds, String runId) {
        if (output == null || cutoff == null || installationId == null || installationId.trim().isEmpty()
                || pageSize < 1 || pageSize > 10000 || timeoutSeconds < 1) {
            throw new IllegalArgumentException("Invalid extraction configuration.");
        }
        this.output = output;
        this.installationId = installationId;
        if (runId != null && !java.util.UUID.fromString(runId).toString().equals(runId)) throw new IllegalArgumentException("Invalid authorized run ID.");
        this.runId = runId;
        this.cutoff = cutoff;
        this.pageSize = pageSize;
        this.timeoutSeconds = timeoutSeconds;
    }

    public static ExtractionOptions fromSystemProperties() {
        return new ExtractionOptions(Paths.get(System.getProperty("integrator.output", "exports")),
            System.getProperty("integrator.installation", "local-esus"),
            LocalDate.parse(System.getProperty("integrator.cutoff", LocalDate.now().toString())),
            Integer.parseInt(System.getProperty("integrator.pageSize", "1000")),
            Integer.parseInt(System.getProperty("integrator.timeoutSeconds", "30")));
    }
}
