package br.com.bancadoingresso.integrator.view;

import br.com.bancadoingresso.integrator.api.*;
import br.com.bancadoingresso.integrator.service.IntegratorService;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import javax.swing.*;
import javax.swing.event.*;

/** Interactive secrets only; IDs come from a verified API response. */
final class InstallationForm extends JPanel {
    private static final long serialVersionUID = 1L;
    final JTextField api = new JTextField(System.getProperty("integrator.api", ""), 24);
    final JPasswordField password = new JPasswordField(24), code = new JPasswordField(24);
    final JCheckBox reactivate = new JCheckBox("Usar um novo código de ativação");
    private final JLabel codeLabel = new JLabel("Código de ativação:");
    InstallationForm() {
        super(new GridBagLayout()); setBorder(BorderFactory.createTitledBorder("Acesso à integração"));
        addRow(0,new JLabel("URL HTTPS da API:"),api);
        addRow(1,new JLabel("Senha local (mín. 12 caracteres):"),password);
        addRow(2,codeLabel,code);
        GridBagConstraints c = new GridBagConstraints(); c.gridx=1;c.gridy=3;c.anchor=GridBagConstraints.WEST;add(reactivate,c);
        reactivate.addActionListener(e -> refresh());
        api.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e){refresh();} public void removeUpdate(DocumentEvent e){refresh();} public void changedUpdate(DocumentEvent e){refresh();}
        }); refresh();
    }
    private void addRow(int row,JLabel label,JTextField field) {
        GridBagConstraints c=new GridBagConstraints();c.gridy=row;c.gridx=0;c.insets=new Insets(3,3,3,3);c.anchor=GridBagConstraints.WEST;
        label.setLabelFor(field);add(label,c);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;add(field,c);
    }
    void refresh() {
        boolean stored=false;
        try {URI origin=new HttpTransport().base(api.getText().trim());stored=new InstallationTokenStore(InstallationTokenStore.defaultPath(origin),origin).exists();}
        catch(IOException e){ /* An incomplete origin has no usable stored identity. */ }
        boolean required=!stored || reactivate.isSelected();
        boolean changed=code.isVisible()!=required;
        code.setVisible(required);codeLabel.setVisible(required);
        if(!required && code.getDocument().getLength()>0)code.setText("");
        if(changed) {revalidate();Window window=SwingUtilities.getWindowAncestor(this);if(window!=null)window.pack();repaint();}
    }
    boolean ready() {
        char[] secret=password.getPassword(),activation=code.getPassword();
        try{return secret.length>=12 && (!code.isVisible() || activation.length>0);}
        finally{Arrays.fill(secret,'\0');Arrays.fill(activation,'\0');}
    }
    void requireActivation(){reactivate.setSelected(true);refresh();}
    Input capture() {
        Input input=new Input(api.getText().trim(),password.getPassword(),code.getPassword());password.setText("");code.setText("");return input;
    }
    static final class Input {
        final String api;final char[] password,code;
        String installation,run;
        Input(String api,char[] password,char[] code){this.api=api;this.password=password;this.code=code;}
        IntegratorService connect(boolean resume)throws IOException {
            HttpTransport http=new HttpTransport();URI origin=http.base(api);
            InstallationClient client=new InstallationClient(origin,http,ApiSignatureVerifier.bundled(),new InstallationTokenStore(InstallationTokenStore.defaultPath(origin),origin));
            client.open(code,password);
            // Recovery must remain possible after a registered run has advanced.
            if(!resume){InstallationClient.CurrentRun current=client.current();installation=current.installation;run=current.run;}
            return new IntegratorService(new FileAvailabilityAPI(client),http);
        }
        void clear(){Arrays.fill(password,'\0');Arrays.fill(code,'\0');}
    }
}
