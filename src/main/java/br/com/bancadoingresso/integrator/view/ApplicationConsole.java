package br.com.bancadoingresso.integrator.view;

import br.com.bancadoingresso.integrator.extraction.ExtractionOptions;
import br.com.bancadoingresso.integrator.service.IntegratorService;
import br.com.bancadoingresso.integrator.util.DatabaseProperties;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** Interactive command-line entry point for the same delivery flow used by the desktop screen. */
public class ApplicationConsole {
    private static final String DEFAULT_DATABASE_URL = "localhost";
    private static final String DEFAULT_DATABASE_PORT = "5432";

    private final BufferedReader input;
    private final PrintStream output;
    private final PrintStream error;
    private final Console console;

    public ApplicationConsole() {
        this(new BufferedReader(new InputStreamReader(System.in)), System.out, System.err, System.console());
    }

    ApplicationConsole(BufferedReader input, PrintStream output, PrintStream error, Console console) {
        this.input = input;
        this.output = output;
        this.error = error;
        this.console = console;
    }

    public void start() throws IOException {
        output.println("Integrador Saúde Agora");
        String resumeFile = readLine("Arquivo ZIP para retomar (deixe vazio para nova integração): ");
        InstallationForm form = new InstallationForm();
        form.code.setText(readPassword("Código de ativação (deixe vazio para usar o token local): "));
        InstallationForm.Input installation = form.capture();

        char[] password = new char[0];
        IntegratorService service = null;
        try {
            Path archive;
            if (resumeFile.trim().isEmpty()) {
                DatabaseInput database = readDatabaseInput();
                password = database.password;
                output.println("Autenticando instalação");
                service = installation.connect(false);
                DatabaseProperties.creatInstance(database.url, database.port, database.database, database.user,
                    new String(password));
                ExtractionOptions defaults = ExtractionOptions.fromApplicationProperties();
                ExtractionOptions options = new ExtractionOptions(defaults.output, installation.installation,
                    defaults.cutoff, defaults.pageSize, defaults.timeoutSeconds, installation.run);
                archive = service.integrate(options, output::println);
            } else {
                Path resume = Paths.get(resumeFile.trim()).toAbsolutePath().normalize();
                if (!Files.isRegularFile(resume)) {
                    throw new IOException("O arquivo ZIP informado não existe ou não pode ser lido.");
                }
                output.println("Autenticando instalação");
                service = installation.connect(true);
                archive = service.resume(resume, output::println);
            }
            output.println("Arquivo registrado: " + archive.toAbsolutePath());
            output.println("O registro não significa que a carga foi processada ou publicada.");
            output.println("Integração concluída. Finalizando aplicação.");
        } catch (Exception e) {
            error.println("Erro na integração: " + integrationErrorMessage(e));
        } finally {
            Arrays.fill(password, '\0');
            installation.clear();
        }
    }

    private DatabaseInput readDatabaseInput() throws IOException {
        String url = readWithDefault("URL do banco de dados", DEFAULT_DATABASE_URL);
        String port = readWithDefault("Porta do banco de dados", DEFAULT_DATABASE_PORT);
        String database = readRequired("Banco de dados");
        String user = readRequired("Usuário");
        return new DatabaseInput(url, port, database, user, readPassword("Senha: ").toCharArray());
    }

    private String readWithDefault(String label, String defaultValue) throws IOException {
        String value = readLine(label + " [" + defaultValue + "]: ").trim();
        return value.isEmpty() ? defaultValue : value;
    }

    private String readRequired(String label) throws IOException {
        while (true) {
            String value = readLine(label + ": ").trim();
            if (!value.isEmpty()) {
                return value;
            }
            error.println(label + " é obrigatório.");
        }
    }

    private String readPassword(String prompt) throws IOException {
        if (console != null) {
            char[] value = console.readPassword("%s", prompt);
            if (value == null) {
                throw new IOException("A entrada foi encerrada antes da integração ser concluída.");
            }
            try {
                return new String(value);
            } finally {
                Arrays.fill(value, '\0');
            }
        }
        return readLine(prompt);
    }

    private String readLine(String prompt) throws IOException {
        if (console != null) {
            String value = console.readLine("%s", prompt);
            if (value == null) {
                throw new IOException("A entrada foi encerrada antes da integração ser concluída.");
            }
            return value;
        }
        output.print(prompt);
        String value = input.readLine();
        if (value == null) {
            throw new IOException("A entrada foi encerrada antes da integração ser concluída.");
        }
        return value;
    }

    private String integrationErrorMessage(Exception exception) {
        if (exception instanceof IOException && exception.getMessage() != null) {
            return exception.getMessage();
        }
        return "Integração não confirmada. Verifique a configuração, a autorização e a conexão com o banco.";
    }

    private static final class DatabaseInput {
        private final String url;
        private final String port;
        private final String database;
        private final String user;
        private final char[] password;

        private DatabaseInput(String url, String port, String database, String user, char[] password) {
            this.url = url;
            this.port = port;
            this.database = database;
            this.user = user;
            this.password = password;
        }
    }
}
