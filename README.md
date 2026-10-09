# Health Now Integrator

Java 8 Swing application for read-only PostgreSQL extraction and secure file delivery.

## Build and run

Use a Java 8 JDK (not only a JRE) and Maven:

```bash
mvn clean verify
java -jar target/integrator-1.0.0-jar-with-dependencies.jar
```

Enter the database connection and, on first use, the one-time activation code. The
HTTP or HTTPS API URL is packaged in `src/main/resources/application.properties`. Later
starts use the local owner-only token file and do not require the code.
The same file contains the non-secret extraction settings, including output
directory, cutoff date, page size and timeout.
The API resolves installation and run IDs: the user never enters UUIDs or generates
keys. The JAR contains only the API public verification keyring, no shared secrets,
private keys, AWS credentials or .env configuration.

The integrator generates immutable JSONL, manifest, reconciliation report and ZIP
files under exports. It verifies signed API responses, uploads using a short-lived
S3 authorization and confirms availability. Use **Retomar arquivo...** to recover
an existing ZIP without querying the source again. Registration means custody,
not processing or publication; the Scheduler performs those subsequent stages.

See [Database extraction](docs/DATABASE_EXTRACTION.md),
[Secure delivery](docs/LOAD_DELIVERY.md) and [Architecture](docs/ARCHITECTURE.md).
