/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.betabridge;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/** A reversible, collision-free legacy name for a non-zero unsigned 64-bit XUID. */
public record LegacyIdentity(String alias, UUID offlineUuid) {
    public static LegacyIdentity fromXuid(String xuid) {
        if (xuid == null || xuid.isEmpty() || xuid.length() > 20 || xuid.charAt(0) == '0') {
            throw new IllegalArgumentException("A canonical, non-zero unsigned XUID is required");
        }
        for (int i = 0; i < xuid.length(); i++) {
            if (xuid.charAt(i) < '0' || xuid.charAt(i) > '9') {
                throw new IllegalArgumentException("XUID must contain ASCII decimal digits only");
            }
        }
        final long value;
        try {
            value = Long.parseUnsignedLong(xuid);
        } catch (NumberFormatException e) {
            // Do not retain the parser's message: it includes the supplied identity.
            throw new IllegalArgumentException("XUID is outside the unsigned 64-bit range");
        }
        String alias = "BE_" + Long.toUnsignedString(value, 36).toUpperCase(Locale.ROOT);
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + alias).getBytes(StandardCharsets.UTF_8));
        return new LegacyIdentity(alias, uuid);
    }
}
