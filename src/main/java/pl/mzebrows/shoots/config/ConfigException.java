// src/main/java/pl/mzebrows/shoots/config/ConfigException.java
package pl.mzebrows.shoots.config;

/**
 * Thrown when a required configuration property is missing or unparseable. The configuration files
 * ({@code game.properties} + {@code graphic.properties}) are the single source of truth -- there are no
 * code-side defaults -- so a missing key is a fatal, fail-fast error: the application logs it and exits.
 */
public class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }
}
