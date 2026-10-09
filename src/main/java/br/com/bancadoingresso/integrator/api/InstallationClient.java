package br.com.bancadoingresso.integrator.api;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;

/** No client key, session exchange, automatic activation replay or token refresh. */
public final class InstallationClient {
    public final URI api;
    private final HttpTransport http;
    private final ApiSignatureVerifier verifier;
    private final InstallationTokenStore store;
    private JsonObject credential;
    public InstallationClient(URI api,HttpTransport http,ApiSignatureVerifier verifier,InstallationTokenStore store) {
        this.api=api;this.http=http;this.verifier=verifier;this.store=store;
    }
    public void open(char[] code,char[] password) throws IOException {
        try {
            if(code.length==0) {credential=store.load(password);return;}
            if(password.length<12)throw new IOException("A senha local deve ter pelo menos 12 caracteres.");
            JsonObject body=new JsonObject();body.addProperty("activation_code",new String(code).trim());
            Arrays.fill(code,'\0');
            JsonObject response;
            try {response=http.json(api,"POST","/integration/v1/esus-installations/credential-activations",body,null,null,null);}
            catch(HttpTransport.Failure e){if(e.status==401)throw new ActivationRequired();throw e;}
            verifier.verify(response,"health-now-installation-token-v1","installation_token_id","installation_token","expires_at");
            JsonObject candidate=response.deepCopy();candidate.addProperty("api",api.toString());
            InstallationTokenStore.validate(candidate,api);
            try {store.save(candidate,password);}
            catch(IOException e){throw new IOException("Ativação aceita, mas o token não pôde ser salvo. Corrija as permissões locais e solicite um novo código antes de tentar novamente.");}
            credential=candidate;
        } finally {Arrays.fill(code,'\0');}
    }
    public CurrentRun current() throws IOException {
        JsonObject response=request("GET","/integration/v1/esus-imports/current",null,null,null);
        verifier.verify(response,"health-now-current-run-v1","installation_id","run_id","state","source_version","mapping_version");
        return new CurrentRun(response);
    }
    public void verifyUpload(JsonObject response) throws IOException {
        verifier.verify(response,"health-now-upload-authorization-v1","file_id","run_id","state","object_key","upload_url","expires_at");
    }
    JsonObject request(String method,String path,JsonObject body,String operation,String key)throws IOException {
        if(credential==null)throw new ActivationRequired();InstallationTokenStore.validate(credential,api);
        try{return http.json(api,method,path,body,ApiSignatureVerifier.text(credential,"installation_token"),operation,key);}
        catch(HttpTransport.Failure e){if(e.status==401){credential=null;throw new ActivationRequired();}throw e;}
    }
    public static final class CurrentRun {
        public final String installation,run,sourceVersion,mappingVersion;
        CurrentRun(JsonObject value)throws IOException {
            installation=ApiSignatureVerifier.uuid(value,"installation_id");run=ApiSignatureVerifier.uuid(value,"run_id");
            sourceVersion=ApiSignatureVerifier.text(value,"source_version");mappingVersion=ApiSignatureVerifier.text(value,"mapping_version");
            if(!"Receiving".equals(ApiSignatureVerifier.text(value,"state")) || !"esus-local-1".equals(mappingVersion)
                    || !sourceVersion.matches("[A-Za-z0-9_.-]{1,64}"))throw new IOException("Execução indisponível ou incompatível com esta versão do integrador.");
        }
    }
}
