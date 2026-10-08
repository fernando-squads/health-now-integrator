package br.com.bancadoingresso.integrator.view;

import br.com.bancadoingresso.integrator.api.*;
import br.com.bancadoingresso.integrator.service.IntegratorService;
import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import javax.swing.*;

/** Non-secret configuration and interactive secrets; no .env or bundled credentials. */
final class InstallationForm extends JPanel {
    private static final long serialVersionUID = 1L;
    final JTextField api = new JTextField(System.getProperty("integrator.api", ""), 24);
    final JTextField installation = new JTextField(System.getProperty("integrator.installation", ""), 24);
    final JTextField run = new JTextField(System.getProperty("integrator.run", ""), 24);
    final JTextField keyPath = new JTextField(System.getProperty("integrator.keystore",
        Paths.get(System.getProperty("user.home"), ".health-now-integrator", "identity.jceks").toString()), 24);
    final JPasswordField password = new JPasswordField(24), code = new JPasswordField(24);
    InstallationForm() {
        super(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("Identidade da instalação"));
        String[] labels = {"URL HTTPS da API:", "Instalação (UUID):", "Execução autorizada (UUID):", "Keystore local:", "Senha local (mín. 12 caracteres):", "Código de ativação (primeiro uso):"};
        JTextField[] fields = {api, installation, run, keyPath, password, code};
        for (int row = 0; row < fields.length; row++) {
            GridBagConstraints c = new GridBagConstraints(); c.gridy = row; c.gridx = 0;
            c.insets = new Insets(3, 3, 3, 3); c.anchor = GridBagConstraints.WEST;
            JLabel label = new JLabel(labels[row]); label.setLabelFor(fields[row]); add(label, c);
            c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; add(fields[row], c);
        }
    }
    Input capture() {
        Input input = new Input(api.getText().trim(), installation.getText().trim(), run.getText().trim(), keyPath.getText().trim(), password.getPassword(), code.getPassword());
        password.setText(""); code.setText(""); return input;
    }
    static final class Input {
        final String api, installation, run, path;
        final char[] password, code;
        Input(String api, String installation, String run, String path, char[] password, char[] code) {
            this.api = api; this.installation = installation; this.run = run; this.path = path; this.password = password; this.code = code;
        }
        IntegratorService connect() throws IOException {
            HttpTransport http = new HttpTransport();
            InstallationKey key = InstallationKey.open(Paths.get(path), password, installation, http.base(api));
            InstallationSessionClient session = new InstallationSessionClient(key, http);
            if (code.length > 0) session.activate(code);
            session.token();
            return new IntegratorService(new FileAvailabilityAPI(http, session), http);
        }
        void clear() { Arrays.fill(password, '\0'); Arrays.fill(code, '\0'); }
    }
}
