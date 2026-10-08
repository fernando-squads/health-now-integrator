package br.com.bancadoingresso.integrator.view;

import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;
import java.nio.file.Path;
import br.com.bancadoingresso.integrator.extraction.ExtractionOptions;
import br.com.bancadoingresso.integrator.service.IntegratorService;
import javax.swing.JFileChooser;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import br.com.bancadoingresso.integrator.util.DatabaseProperties;

public class ApplicationSwing {
	private static final String DEFAULT_DATABASE_URL = "localhost";
	private static final String DEFAULT_DATABASE_PORT = "5432";
	private static final String DATABASE_CONFIGURATION_CARD = "databaseConfiguration";
	private static final String INTEGRATION_CARD = "integration";

	private JLabel integrationStatusLabel;
	private JProgressBar integrationProgressBar;
	private JScrollPane errorScrollPane;
	private JTextArea errorTextArea;
	private JButton backButton;
	private JPanel integrationPanel;
	private InstallationForm installationForm;
	private Path pendingArchive;

	public void start() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				createAndShowWindow();
			}
		});
	}

	private void createAndShowWindow() {
		JFrame frame = new JFrame("Integrador Saúde Agora");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		JPanel cards = new JPanel(new CardLayout());
		cards.add(createDatabaseConfigurationPanel(cards), DATABASE_CONFIGURATION_CARD);
		cards.add(createIntegrationPanel(cards), INTEGRATION_CARD);
		frame.setContentPane(cards);
		frame.pack();
		frame.setResizable(false);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private JPanel createDatabaseConfigurationPanel(final JPanel cards) {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.insets = new Insets(6, 6, 6, 6);
		constraints.anchor = GridBagConstraints.WEST;

		JTextField urlField = new JTextField(DEFAULT_DATABASE_URL, 24);
		JTextField portField = new JTextField(DEFAULT_DATABASE_PORT, 24);
		JTextField databaseField = new JTextField(24);
		JTextField userField = new JTextField(24);
		JPasswordField passwordField = new JPasswordField(24);
		JButton nextButton = new JButton("Próximo");

		addTitle(panel, constraints);
		addField(panel, constraints, 1, "URL:", urlField);
		addField(panel, constraints, 2, "Porta:", portField);
		addField(panel, constraints, 3, "Banco de dados:", databaseField);
		addField(panel, constraints, 4, "Usuário:", userField);
		addField(panel, constraints, 5, "Senha:", passwordField);
		installationForm = new InstallationForm();
		constraints.gridx = 0; constraints.gridy = 6; constraints.gridwidth = 2;
		constraints.fill = GridBagConstraints.HORIZONTAL; panel.add(installationForm, constraints);
		addNextButton(panel, constraints, nextButton);
		JTextField[] required = {urlField, portField, databaseField, userField, passwordField,
            installationForm.api, installationForm.installation, installationForm.run, installationForm.keyPath, installationForm.password};
		updateNextButtonState(nextButton, required);
		addRequiredFieldListener(nextButton, required);
		nextButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				showIntegrationScreen(cards);
				testDatabaseConnection(urlField.getText().trim(), portField.getText().trim(),
						databaseField.getText().trim(), userField.getText().trim(), passwordField.getPassword(), installationForm.capture(), pendingArchive);
				passwordField.setText("");
			}
		});
		JButton resume = new JButton("Retomar arquivo...");
		constraints.gridx = 0; constraints.gridy = 8; constraints.gridwidth = 2;
		panel.add(resume, constraints);
		resume.addActionListener(event -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Arquivo de carga ZIP", "zip"));
			if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
				showIntegrationScreen(cards);
				testDatabaseConnection("", "", "", "", new char[0], installationForm.capture(), chooser.getSelectedFile().toPath());
			}
		});

		return panel;
	}

	private JPanel createIntegrationPanel(final JPanel cards) {
		integrationPanel = new JPanel(new GridBagLayout());
		integrationPanel.setBorder(BorderFactory.createEmptyBorder(48, 48, 48, 48));

		JLabel title = new JLabel("Realizando integração", SwingConstants.CENTER);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 18.0f));
		integrationProgressBar = new JProgressBar();
		integrationProgressBar.setIndeterminate(true);
		integrationProgressBar.setPreferredSize(new Dimension(280, 20));
		integrationStatusLabel = new JLabel("Testando conexão", SwingConstants.CENTER);
		errorTextArea = new JTextArea(4, 24);
		errorTextArea.setEditable(false);
		errorTextArea.setLineWrap(true);
		errorTextArea.setWrapStyleWord(true);
		errorScrollPane = new JScrollPane(errorTextArea);
		errorScrollPane.setPreferredSize(new Dimension(280, 80));
		errorScrollPane.setVisible(false);
		backButton = new JButton("Voltar");
		backButton.setVisible(false);
		backButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				showDatabaseConfigurationScreen(cards);
			}
		});

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(6, 6, 12, 6);
		integrationPanel.add(title, constraints);

		constraints.gridy = 1;
		constraints.insets = new Insets(0, 6, 6, 6);
		integrationPanel.add(integrationProgressBar, constraints);

		constraints.gridy = 2;
		integrationPanel.add(integrationStatusLabel, constraints);

		constraints.gridy = 3;
		integrationPanel.add(errorScrollPane, constraints);

		constraints.gridy = 4;
		constraints.anchor = GridBagConstraints.EAST;
		constraints.fill = GridBagConstraints.NONE;
		integrationPanel.add(backButton, constraints);

		return integrationPanel;
	}

	private void showIntegrationScreen(JPanel cards) {
		resetIntegrationFeedback();
		CardLayout layout = (CardLayout) cards.getLayout();
		layout.show(cards, INTEGRATION_CARD);
	}

	private void showDatabaseConfigurationScreen(JPanel cards) {
		CardLayout layout = (CardLayout) cards.getLayout();
		layout.show(cards, DATABASE_CONFIGURATION_CARD);
	}

	private void testDatabaseConnection(final String url, final String port, final String database, final String user,
			final char[] password, final InstallationForm.Input input, final Path resume) {
		new SwingWorker<Void, String>() {
			private String errorMessage;
			private Path archive;

			@Override
			protected Void doInBackground() {
				IntegratorService service = null;
				try {
					publish("Autenticando instalação");
					service = input.connect();
					if (resume != null) { archive = service.resume(resume, message -> publish(message)); }
					else {
						DatabaseProperties.creatInstance(url, port, database, user, new String(password));
						ExtractionOptions defaults = ExtractionOptions.fromSystemProperties();
						ExtractionOptions options = new ExtractionOptions(defaults.output, input.installation, defaults.cutoff,
							defaults.pageSize, defaults.timeoutSeconds, input.run);
						archive = service.integrate(options, message -> publish(message));
					}
				} catch (Exception e) {
					errorMessage = e instanceof java.io.IOException ? e.getMessage()
                        : "Integração não confirmada. Verifique a configuração, a autorização e a conexão com o banco.";
				} finally {
					Arrays.fill(password, '\0');
					input.clear();
					if (service != null) pendingArchive = service.pendingArchive();
				}
				return null;
			}

            @Override
            protected void process(List<String> messages) {
                integrationStatusLabel.setText(messages.get(messages.size() - 1));
            }

			@Override
			protected void done() {
				if (errorMessage == null) {
					showConnectionSuccess();
					errorTextArea.setText("Arquivo registrado: " + archive.toAbsolutePath()
                        + "\nO registro não significa que a carga foi processada ou publicada.");
					errorScrollPane.setVisible(true);
					backButton.setVisible(true);
					integrationPanel.revalidate();
				} else {
					showIntegrationError(errorMessage);
				}
			}
		}.execute();
	}

	private void showConnectionSuccess() {
		integrationProgressBar.setIndeterminate(false);
		integrationProgressBar.setValue(100);
		integrationStatusLabel.setText("Arquivo registrado; aguardando processamento");
	}

	public void showIntegrationError(final String errorMessage) {
		if (SwingUtilities.isEventDispatchThread()) {
			displayIntegrationError(errorMessage);
			return;
		}

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				displayIntegrationError(errorMessage);
			}
		});
	}

	private void displayIntegrationError(String errorMessage) {
		integrationProgressBar.setIndeterminate(false);
		integrationStatusLabel.setText("Erro na integração");
		errorTextArea.setText(errorMessage);
		errorScrollPane.setVisible(true);
		backButton.setVisible(true);
		integrationPanel.revalidate();
		integrationPanel.repaint();
	}

	private void resetIntegrationFeedback() {
		integrationProgressBar.setValue(0);
		integrationProgressBar.setIndeterminate(true);
		integrationStatusLabel.setText("Testando conexão");
		errorTextArea.setText("");
		errorScrollPane.setVisible(false);
		backButton.setVisible(false);
	}

	private void addTitle(JPanel panel, GridBagConstraints constraints) {
		JLabel title = new JLabel("Informe os dados abaixo para conectar ao banco de dados:");
		title.setFont(title.getFont().deriveFont(Font.BOLD));

		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.gridwidth = 2;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		panel.add(title, constraints);
	}

	private void addField(JPanel panel, GridBagConstraints constraints, int row, String labelText, JTextField field) {
		JLabel label = new JLabel(labelText);
		label.setLabelFor(field);

		constraints.gridx = 0;
		constraints.gridy = row;
		constraints.gridwidth = 1;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		panel.add(label, constraints);

		constraints.gridx = 1;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		panel.add(field, constraints);
	}

	private void addNextButton(JPanel panel, GridBagConstraints constraints, JButton nextButton) {
		constraints.gridx = 1;
		constraints.gridy = 7;
		constraints.gridwidth = 1;
		constraints.weightx = 1;
		constraints.anchor = GridBagConstraints.EAST;
		constraints.fill = GridBagConstraints.NONE;
		panel.add(nextButton, constraints);
	}

	private void addRequiredFieldListener(final JButton nextButton, final JTextField... fields) {
		DocumentListener listener = new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				updateNextButtonState(nextButton, fields);
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				updateNextButtonState(nextButton, fields);
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				updateNextButtonState(nextButton, fields);
			}
		};

		for (JTextField field : fields) {
			field.getDocument().addDocumentListener(listener);
		}
	}

	private void updateNextButtonState(JButton nextButton, JTextField... fields) {
		for (JTextField field : fields) {
			if (field.getText().trim().isEmpty()) {
				nextButton.setEnabled(false);
				return;
			}
		}
		nextButton.setEnabled(true);
	}
}
