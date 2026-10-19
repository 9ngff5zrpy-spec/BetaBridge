/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.betabridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Properties;

/** Beta stores player data by login name. Move that file when an authenticated XUID changes names. */
public final class LegacyPlayerNames {
    private final Path registryFile;
    private final Path players;
    private final Properties names = new Properties();

    public LegacyPlayerNames(Path registryFile, Path players) throws IOException {
        this.registryFile = registryFile;
        this.players = players;
        if (Files.exists(registryFile)) {
            try (InputStream input = Files.newInputStream(registryFile)) { names.load(input); }
        }
    }

    public static String displayName(String xuid, String gamertag) {
        LegacyIdentity identity = LegacyIdentity.fromXuid(xuid);
        if (gamertag == null || gamertag.isBlank()) throw new IllegalArgumentException("Missing gamertag");
        String readable = gamertag.replaceAll("[^A-Za-z0-9_]", "_");
        if (readable.length() <= 15) return "." + readable;
        String suffix = identity.alias().substring(3);
        suffix = suffix.substring(Math.max(0, suffix.length() - 7));
        return "." + readable.substring(0, 7) + "_" + suffix;
    }

    public synchronized String resolve(String xuid, String gamertag) throws IOException {
        LegacyIdentity identity = LegacyIdentity.fromXuid(xuid);
        String key = "last." + xuid;
        String pendingKey = "next." + xuid;
        String previous = names.getProperty(key, identity.alias());
        String pending = names.getProperty(pendingKey);
        String desired = displayName(xuid, gamertag);
        Files.createDirectories(players);

        if (conflicts(xuid, desired, previous, pending)) {
            desired = ".B" + identity.alias().substring(3);
            if (conflicts(xuid, desired, previous, pending)) {
                throw new IOException("The legacy name is already in use; no player data was overwritten");
            }
        }
        // A small journal makes a rename recoverable across a crash between move and registry commit.
        names.setProperty(pendingKey, desired);
        save();
        Path destination = playerFile(desired);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (pending != null) candidates.add(pending);
        candidates.add(previous);
        candidates.add(identity.alias());
        Path source = null;
        for (String candidate : candidates) {
            Path path = playerFile(candidate);
            if (Files.exists(path)) { source = path; break; }
        }
        if (source != null && !source.equals(destination)) {
            if (!Files.exists(destination) || !Files.isSameFile(source, destination)) {
                if (Files.exists(destination)) throw new IOException("Refusing to overwrite another player file");
                Files.move(source, destination);
            }
        }
        names.setProperty(key, desired);
        names.remove(pendingKey);
        save();
        return desired;
    }

    private boolean conflicts(String xuid, String desired, String previous, String pending) throws IOException {
        for (String key : names.stringPropertyNames()) {
            if ((key.startsWith("last.") || key.startsWith("next."))
                && !key.endsWith("." + xuid) && names.getProperty(key).equalsIgnoreCase(desired)) return true;
        }
        Path file = playerFile(desired);
        return Files.exists(file) && !desired.equalsIgnoreCase(previous)
            && (pending == null || !desired.equalsIgnoreCase(pending));
    }

    private Path playerFile(String name) throws IOException {
        if (name == null || !name.matches("[.A-Za-z0-9_]{1,16}") || name.equals(".") || name.equals("..")) {
            throw new IOException("Invalid legacy player filename");
        }
        return players.resolve(name + ".dat");
    }

    private void save() throws IOException {
        Files.createDirectories(registryFile.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(registryFile.toAbsolutePath().getParent(), "names-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                names.store(output, "BetaBridge authenticated XUID to Beta player-name registry");
            }
            try {
                Files.move(temporary, registryFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, registryFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
