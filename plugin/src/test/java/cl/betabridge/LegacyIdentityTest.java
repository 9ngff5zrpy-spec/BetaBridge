/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.betabridge;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Plain Java contract tests: run main(), no test framework or server required. */
public final class LegacyIdentityTest {
    public static void main(String[] args) {
        expected("1", "BE_1");
        expected("35", "BE_Z");
        expected("36", "BE_10");
        expected("18446744073709551615", "BE_3W5E11264SGSF");
        check(LegacyIdentity.fromXuid("18446744073709551615").alias().length() == 16,
            "Unsigned maximum fits the Beta username limit exactly");

        String[] invalid = {null, "", "0", "00", "01", "+1", "-1", " 1", "1 ", "1\n",
            "1.0", "１", "١", "18446744073709551616", "99999999999999999999", "100000000000000000000"};
        for (String xuid : invalid) {
            try {
                LegacyIdentity.fromXuid(xuid);
                throw new AssertionError("Invalid identity was accepted");
            } catch (IllegalArgumentException expected) {
                check(!expected.getMessage().contains("999999"), "Error does not disclose input");
            }
        }

        Set<String> aliases = new HashSet<>();
        Set<UUID> uuids = new HashSet<>();
        long[] boundaries = {1, 2, 35, 36, 37, Long.MAX_VALUE - 1, Long.MAX_VALUE, Long.MIN_VALUE,
            Long.MIN_VALUE + 1, -2, -1};
        for (long value : boundaries) verifyRoundTrip(value, aliases, uuids);
        Random random = new Random(0xB1401L);
        for (int i = 0; i < 100_000; i++) {
            long value = random.nextLong();
            if (value != 0) verifyRoundTrip(value, aliases, uuids);
        }

        // Known offline-profile convention, checked against an independently fixed fixture.
        check(LegacyIdentity.fromXuid("1").offlineUuid().equals(UUID.fromString("d2c9e548-fa37-3b6d-96e5-8cc48a432306")),
            "Offline profile UUID remains stable for the playerfile alias");
        check(LegacyIdentity.fromXuid("2533274800000001").equals(LegacyIdentity.fromXuid("2533274800000001")),
            "Reconnections produce the same identity without gamertag input");
        System.out.println("LegacyIdentityTest passed: boundary, invalid-input, 100000 round-trip and uniqueness cases.");
    }

    private static void expected(String decimal, String alias) {
        check(LegacyIdentity.fromXuid(decimal).alias().equals(alias), "Expected alias for contract fixture");
    }

    private static void verifyRoundTrip(long value, Set<String> aliases, Set<UUID> uuids) {
        String decimal = Long.toUnsignedString(value);
        LegacyIdentity identity = LegacyIdentity.fromXuid(decimal);
        check(identity.alias().matches("BE_[0-9A-Z]{1,13}"), "Legacy username characters and length");
        BigInteger decoded = new BigInteger(identity.alias().substring(3), 36);
        check(decoded.equals(new BigInteger(decimal)), "Alias decodes to the full unsigned identity without truncation");
        check(aliases.add(identity.alias()), "Different identity examples cannot share an alias");
        check(uuids.add(identity.offlineUuid()), "Different examples cannot share an offline profile");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
