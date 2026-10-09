package br.com.bancadoingresso.integrator.view;

import br.com.bancadoingresso.integrator.api.InstallationTokenStore;
import java.awt.Component;
import java.net.URI;
import java.nio.file.*;
import javax.swing.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class InstallationFormTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void firstUseHasNoUuidOrKeyPathFieldsAndRequiresActivation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            InstallationForm form=new InstallationForm();form.api.setText("https://synthetic-first-use.invalid");
            int fields=0;
            for(Component c:form.getComponents()) {
                if(c instanceof JTextField)fields++;
                if(c instanceof JLabel)assertFalse(((JLabel)c).getText().contains("UUID"));
            }
            assertEquals(3,fields);assertTrue(form.code.isVisible());assertFalse(form.ready());
            form.password.setText("synthetic-local-password");assertFalse(form.ready());form.code.setText("synthetic-code");assertTrue(form.ready());
        });
    }
    @Test public void storedCredentialHidesCodeAndReactivationRestoresIt() throws Exception {
        String previous=System.getProperty("user.home");
        try {
            System.setProperty("user.home",temporary.getRoot().getAbsolutePath());
            Path path=InstallationTokenStore.defaultPath(URI.create("https://synthetic-stored.invalid/"));
            Files.createDirectories(path.getParent());Files.createFile(path);
            SwingUtilities.invokeAndWait(() -> {
                InstallationForm form=new InstallationForm();form.api.setText("https://synthetic-stored.invalid");
                assertFalse(form.code.isVisible());form.password.setText("synthetic-local-password");assertTrue(form.ready());
                form.requireActivation();assertTrue(form.code.isVisible());assertFalse(form.ready());
            });
        } finally {System.setProperty("user.home",previous);}
    }
}
