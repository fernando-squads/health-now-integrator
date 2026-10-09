package br.com.bancadoingresso.integrator.api;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.logging.Logger;

/** No client key, session exchange, automatic activation replay or token refresh. */
public final class InstallationClient {
    private static final Logger LOG = Logger.getLogger(InstallationClient.class.getName());
    public final URI api;
    private final HttpTransport http;
    private final ApiSignatureVerifier verifier;
    private final InstallationTokenStore store;
    private JsonObject credential;
    public InstallationClient(URI api,HttpTransport http,ApiSignatureVerifier verifier,InstallationTokenStore store) {
        this.api=api;this.http=http;this.verifier=verifier;this.store=store;
    }
    public void open(char[] code) throws IOException {
        try {
            if(code.length==0) {credential=store.load();return;}
            JsonObject body=new JsonObject();body.addProperty("activation_code",new String(code).trim());
            Arrays.fill(code,'\0');
            JsonObject response;
            LOG.info("eSUS bootstrap activation requested endpoint=/integration/v1/esus-imports/bootstrap/activations");
            try {response=http.json(api,"POST","/integration/v1/esus-imports/bootstrap/activations",body,null,null,null);}
            catch(HttpTransport.Failure e){
                LOG.warning("eSUS bootstrap activation rejected http_status=" + e.status);
                if(e.status==401)throw new ActivationRequired();throw e;
            }
            verifier.verify(response,"health-now-bootstrap-token-v1","import_id","access_token","expires_at");
            JsonObject candidate=response.deepCopy();candidate.addProperty("api",api.toString());
            InstallationTokenStore.validate(candidate,api);
            try {store.save(candidate);}
            catch(IOException e){throw new IOException("Ativação aceita, mas o token não pôde ser salvo. Corrija as permissões locais e solicite um novo código antes de tentar novamente.");}
            credential=candidate;
            LOG.info("eSUS bootstrap activation accepted; credential stored locally");
        } finally {Arrays.fill(code,'\0');}
    }
    public CurrentRun current() throws IOException {
        JsonObject response=request("GET","/integration/v1/esus-imports/bootstrap/current",null,null,null);
        if (!ApiSignatureVerifier.uuid(credential, "import_id").equals(ApiSignatureVerifier.uuid(response, "import_id"))) {
            throw new ActivationRequired();
        }
        return new CurrentRun(response);
    }
    UploadAuthorization uploadAuthorization(JsonObject response, br.com.bancadoingresso.integrator.service.LoadArtifact artifact) throws IOException {
        verifyUpload(response);
        return new UploadAuthorization(response, artifact, http);
    }
    public void verifyUpload(JsonObject response) throws IOException {
        verifier.verify(response,"health-now-bootstrap-upload-v1","import_id","file_id","upload_url","expires_at");
    }
    JsonObject request(String method,String path,JsonObject body,String operation,String key)throws IOException {
        if(credential==null)throw new ActivationRequired();InstallationTokenStore.validate(credential,api);
        try{return http.json(api,method,path,body,ApiSignatureVerifier.text(credential,"access_token"),operation,key);}
        catch(HttpTransport.Failure e){
            LOG.warning("eSUS bootstrap request rejected method=" + method + " path=" + path + " http_status=" + e.status);
            if(e.status==401){credential=null;throw new ActivationRequired();}throw e;
        }
    }
    public static final class CurrentRun {
        public final String importId;
        CurrentRun(JsonObject value)throws IOException {
            importId=ApiSignatureVerifier.uuid(value,"import_id");
            String state=ApiSignatureVerifier.text(value,"state");
            if(!"activated".equals(state)) throw new IOException("Importação indisponível para envio.");
        }
    }
}
