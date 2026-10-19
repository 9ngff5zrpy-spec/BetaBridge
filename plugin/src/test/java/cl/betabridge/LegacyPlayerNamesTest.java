/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.betabridge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** File migration contracts, including existing-world data and interrupted renames. */
public final class LegacyPlayerNamesTest {
    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("betabridge-names-test-");
        try {
            Path players = temporary.resolve("players");
            Files.createDirectories(players);
            Path registry = temporary.resolve("names.properties");
            String xuid = "2533274800000001";
            Path old = players.resolve(LegacyIdentity.fromXuid(xuid).alias() + ".dat");
            byte[] inventory = {10, 0, 7, 42, 5, 64};
            Files.write(old, inventory);
            var names = new LegacyPlayerNames(registry, players);
            String first = names.resolve(xuid, "Player One");
            check(first.equals(".Player_One"), "Readable Bedrock gamertag");
            check(!Files.exists(old), "Old internal alias migrated");
            check(java.util.Arrays.equals(Files.readAllBytes(players.resolve(first + ".dat")), inventory),
                "Existing inventory bytes preserved exactly");
            names = new LegacyPlayerNames(registry, players);
            check(names.resolve(xuid, "Player One").equals(first), "Reconnect keeps identity");
            String renamed = names.resolve(xuid, "NewTag");
            check(renamed.equals(".NewTag") && !Files.exists(players.resolve(first + ".dat")), "Gamertag change migrates file");
            check(java.util.Arrays.equals(Files.readAllBytes(players.resolve(renamed + ".dat")), inventory),
                "Gamertag change keeps inventory");

            Files.write(players.resolve(".Occupied.dat"), new byte[]{99});
            String alternative = names.resolve("2", "Occupied");
            check(!alternative.equals(".Occupied"), "Unowned Java player file is never reused");
            check(Files.readAllBytes(players.resolve(".Occupied.dat"))[0] == 99, "No overwrite on collision");
            String collision = names.resolve("3", "NewTag");
            check(!collision.equals(".NewTag"), "Different Xbox accounts cannot claim the same file");

            // Simulate a crash after a successful move but before committing the new name.
            Properties journal = new Properties();
            try (var input = Files.newInputStream(registry)) { journal.load(input); }
            journal.setProperty("next." + xuid, ".Recovered");
            try (var output = Files.newOutputStream(registry)) { journal.store(output, "test"); }
            Files.move(players.resolve(".NewTag.dat"), players.resolve(".Recovered.dat"));
            names = new LegacyPlayerNames(registry, players);
            check(names.resolve(xuid, "Recovered").equals(".Recovered"), "Interrupted migration recovers ownership");
            check(java.util.Arrays.equals(Files.readAllBytes(players.resolve(".Recovered.dat")), inventory),
                "Interrupted migration preserves bytes");

            for (String tag : new String[]{"ABCDEFGHIJKLMNOPQRSTUV", "../escape", "玩家", "A#1234"}) {
                String name = LegacyPlayerNames.displayName("18446744073709551615", tag);
                check(name.length() <= 16 && name.matches("[.A-Za-z0-9_]+"), "Legacy name is bounded and path-safe");
            }
            System.out.println("LegacyPlayerNamesTest passed: migration, reconnect, rename, collisions and crash recovery.");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
