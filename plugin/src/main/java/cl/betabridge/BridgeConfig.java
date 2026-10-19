/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.betabridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Own configuration only; this plugin never edits ViaProxy, Geyser, or Beta settings. */
public record BridgeConfig(int proxyPort, int backendPort, Path playerDirectory, Path skinCacheDirectory) {
    public static BridgeConfig load(Path file) throws IOException {
        Properties properties = new Properties();
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            try (InputStream defaults = BridgeConfig.class.getResourceAsStream("/beta-bridge.properties")) {
                if (defaults == null) throw new IOException("Missing BetaBridge default configuration");
                Files.copy(defaults, file);
            }
        }
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        Path playerDirectory = Path.of(properties.getProperty("beta-player-directory", "../beta/world/players"))
            .toAbsolutePath().normalize();
        Path skinCacheDirectory = Path.of(properties.getProperty("skin-cache-directory", "../skin-cache"))
            .toAbsolutePath().normalize();
        return new BridgeConfig(port(properties, "proxy-port"), port(properties, "backend-port"), playerDirectory, skinCacheDirectory);
    }

    private static int port(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException e) {
            throw new IOException("Invalid or missing BetaBridge port: " + key);
        }
    }
}
