package br.com.bancadoingresso.integrator.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Immutable configuration packaged with the desktop application. */
public final class ApplicationProperties {
    private static final String RESOURCE = "/application.properties";
    private static final String API_URL = "integrator.api.url";

    private ApplicationProperties() { }

    public static String apiUrl() {
        return required(API_URL);
    }

    public static String value(String key, String defaultValue) {
        String value = load().getProperty(key);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private static String required(String key) {
        String value = value(key, null);
        if (value == null) {
            throw new IllegalStateException("The application property '" + key + "' is not configured.");
        }
        return value;
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream input = ApplicationProperties.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Application configuration is unavailable.");
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Application configuration cannot be read.", e);
        }
        return properties;
    }
}
