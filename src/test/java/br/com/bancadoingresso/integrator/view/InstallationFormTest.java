package br.com.bancadoingresso.integrator.view;

import br.com.bancadoingresso.integrator.api.InstallationTokenStore;
import java.net.URI;
import java.nio.file.*;
import javax.swing.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class InstallationFormTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void firstUseHasOnlyTheActivationCodeAndRequiresIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            InstallationForm form=new InstallationForm();
            assertTrue(form.code.isVisible());assertFalse(form.ready());
            form.code.setText("synthetic-code");assertTrue(form.ready());
        });
    }
    @Test public void storedCredentialHidesCodeAndActivationErrorRestoresIt() throws Exception {
        String previous=System.getProperty("user.home");
        try {
            System.setProperty("user.home",temporary.getRoot().getAbsolutePath());
            final String api="https://synthetic-stored.invalid";
            Path path=InstallationTokenStore.defaultPath(URI.create(api + "/"));
            Files.createDirectories(path.getParent());Files.createFile(path);
            SwingUtilities.invokeAndWait(() -> {
                InstallationForm form=new InstallationForm(api);
                assertFalse(form.code.isVisible());assertTrue(form.ready());
                form.requireActivation();assertTrue(form.code.isVisible());assertFalse(form.ready());
            });
        } finally {System.setProperty("user.home",previous);}
    }
}
