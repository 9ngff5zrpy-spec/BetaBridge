package cl.nostalgia.betabridge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Contract between session-selected identifiers and the actual generated pack. */
public final class BetaAtmosphereTest {
    public static void main(String[] args) throws Exception {
        Path fogs = Path.of(args[0]);
        Set<String> emitted = new HashSet<>();
        for (long time = -24000; time < 48000; time++) {
            String id = BetaAtmosphere.namespace(time, false, 0);
            if (emitted.add(id)) {
                String json = Files.readString(fogs.resolve(id.substring(id.indexOf(':') + 1) + ".json"));
                check(json.contains("\"identifier\": \"" + id + "\""), "fog identifier exists in pack");
            }
            check(id.equals(BetaAtmosphere.namespace(time + 24000, false, 0)), "day wraps without missing phase");
        }
        check(emitted.size() == 48, "all day/night phases available");
        String nether = BetaAtmosphere.namespace(6000, true, 1);
        check(Files.readString(fogs.resolve("beta_nether.json")).contains(nether), "Nether identifier exists");
        check(BetaAtmosphere.namespace(6000, false, 2) == null, "unsupported dimension leaves fog alone");
        System.out.println("BetaAtmosphereTest passed: 72000 ticks, negative time, day wrap and every pack identifier.");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
