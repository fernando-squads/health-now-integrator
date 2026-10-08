package br.com.bancadoingresso.integrator.service;
import br.com.bancadoingresso.integrator.api.*;
import java.io.IOException;
/** AWS credentials and HEAD verification belong exclusively to the API. */
public final class S3Service {
    private final HttpTransport http;
    public S3Service(HttpTransport http) { this.http = http; }
    public void upload(LoadArtifact artifact, UploadAuthorization authorization) throws IOException {
        if (!authorization.expiresAt.isAfter(java.time.Instant.now())) throw new IOException("Autorização de upload expirada.");
        http.upload(artifact.path, artifact.size, authorization.url, authorization.headers);
    }
}
