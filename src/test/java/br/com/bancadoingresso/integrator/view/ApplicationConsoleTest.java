package br.com.bancadoingresso.integrator.view;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.Rule;
import static org.junit.Assert.assertTrue;

public class ApplicationConsoleTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void requestsActivationCodeForNewIntegration() throws Exception {
        String previousHome = System.getProperty("user.home");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        try {
            System.setProperty("user.home", temporary.getRoot().getAbsolutePath());
            String answers = "\n\n\n\ndatabase\nuser\npassword\n";
            BufferedReader input = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(answers.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
            ApplicationConsole console = new ApplicationConsole(input, new PrintStream(output), new PrintStream(error), null);

            console.start();

            assertTrue(output.toString("UTF-8").contains("Código de ativação"));
            assertTrue(error.toString("UTF-8").contains("Ativação necessária"));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }
}
