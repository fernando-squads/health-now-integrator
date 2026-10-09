package br.com.bancadoingresso.integrator.view;

import br.com.bancadoingresso.integrator.api.*;
import br.com.bancadoingresso.integrator.service.IntegratorService;
import br.com.bancadoingresso.integrator.util.ApplicationProperties;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import javax.swing.*;

/** Interactive activation only; API origin and IDs come from trusted configuration/API responses. */
final class InstallationForm {
    private final String api;
    final JPasswordField code = new JPasswordField(24);
    private boolean activationRequired;
    private JLabel codeLabel;
    private JPanel container;

    InstallationForm() { this(ApplicationProperties.apiUrl()); }
    InstallationForm(String api) { this.api=api; refresh(); }

    void attach(JLabel label, JPanel parent) {
        codeLabel=label;
        container=parent;
        refresh();
    }
    void refresh() {
        boolean stored=false;
        try {URI origin=new HttpTransport().base(api);stored=new InstallationTokenStore(InstallationTokenStore.defaultPath(origin),origin).exists();}
        catch(IOException e){ /* An incomplete origin has no usable stored identity. */ }
        boolean required=!stored || activationRequired;
        boolean changed=code.isVisible()!=required;
        code.setVisible(required);
        if (codeLabel != null) codeLabel.setVisible(required);
        if(!required && code.getDocument().getLength()>0)code.setText("");
        if(changed && container != null) {
            container.revalidate();
            Window window=SwingUtilities.getWindowAncestor(container);
            if(window!=null)window.pack();
            container.repaint();
        }
    }
    boolean ready() {
        char[] activation=code.getPassword();
        try{return !code.isVisible() || activation.length>0;}
        finally{Arrays.fill(activation,'\0');}
    }
    void requireActivation() { activationRequired=true; refresh(); }
    Input capture() {
        Input input=new Input(api,code.getPassword());
        activationRequired=false;
        code.setText("");
        return input;
    }
    static final class Input {
        final String api;final char[] code;
        String installation,run;
        Input(String api,char[] code){this.api=api;this.code=code;}
        IntegratorService connect(boolean resume)throws IOException {
            HttpTransport http=new HttpTransport();URI origin=http.base(api);
            InstallationClient client=new InstallationClient(origin,http,ApiSignatureVerifier.bundled(),new InstallationTokenStore(InstallationTokenStore.defaultPath(origin),origin));
            client.open(code);
            // Recovery must remain possible after a registered run has advanced.
            if(!resume){InstallationClient.CurrentRun current=client.current();installation=current.importId;run=current.importId;}
            return new IntegratorService(new FileAvailabilityAPI(client),http);
        }
        void clear(){Arrays.fill(code,'\0');}
    }
}
