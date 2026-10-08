package br.com.bancadoingresso.integrator.service;

import java.io.IOException;

public interface LoadStorage {
    void validateConfiguration(String installationId) throws IOException;
    String destination(LoadArtifact artifact) throws IOException;
    String objectKey(LoadArtifact artifact) throws IOException;
    void upload(LoadArtifact artifact) throws IOException;
}
