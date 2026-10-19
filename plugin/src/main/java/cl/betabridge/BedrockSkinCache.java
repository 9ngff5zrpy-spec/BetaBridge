/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.betabridge;

import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.BedrockClientData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local, derived skin assets only. Call after the verified Xbox session has been
 * associated with its resolved legacy player name. No identity or player data
 * is changed here, and nothing is uploaded or installed into a Java client.
 *
 * The PNG retains modern outer layers for LegacyFix to convert for Beta. Its
 * local overrides do not carry a slim flag, so slim arms are widened first.
 * Persona/custom models require a fallback; exporting a flat texture does not
 * reproduce their geometry, animation, cosmetics, or capes.
 */
public final class BedrockSkinCache {
    private static final int MAX_RGBA_BYTES = 128 * 128 * 4;
    private static final int MAX_GEOMETRY_NAME_BYTES = 8192;
    private static final Pattern LEGACY_NAME = Pattern.compile("[.A-Za-z0-9_]{1,16}");
    private static final Pattern STANDARD_PATCH = Pattern.compile(
        "\\{\\s*\"geometry\"\\s*:\\s*\\{\\s*\"default\"\\s*:\\s*\"(geometry\\.humanoid(?:\\.custom(?:Slim)?)?)\"\\s*\\}\\s*\\}");

    private BedrockSkinCache() {}

    public enum Status {
        EXPORTED, NO_SKIN, UNSUPPORTED_PERSONA, UNSUPPORTED_DIMENSIONS,
        UNSUPPORTED_GEOMETRY, INVALID_RGBA
    }

    public record Result(Status status, Path png, Path metadata, boolean originalSlim) {
        public boolean exported() { return status == Status.EXPORTED; }
    }

    /** The caller must already have completed BetaBridge's authentication checks. */
    public static Result export(Path cacheDirectory, String visibleName, GeyserSession session) throws IOException {
        if (session == null || session.isClosed() || session.getAuthData() == null) {
            throw new IllegalArgumentException("A live authenticated Bedrock session is required");
        }
        LegacyIdentity.fromXuid(session.xuid());
        BedrockClientData data = session.getClientData();
        if (data == null) {
            return exportPixels(cacheDirectory, visibleName, null, 0, 0, false, false, null);
        }
        return exportPixels(cacheDirectory, visibleName, data.getSkinData(),
            data.getSkinImageWidth(), data.getSkinImageHeight(), data.isPersonaSkin(),
            "slim".equalsIgnoreCase(data.getArmSize()), data.getGeometryName());
    }

    /** Pure input boundary, also usable by the headless conversion tests. */
    static Result exportPixels(Path cacheDirectory, String visibleName, byte[] rgba,
                               int width, int height, boolean persona, boolean slim,
                               byte[] geometryPatch) throws IOException {
        if (visibleName == null || !LEGACY_NAME.matcher(visibleName).matches()
            || visibleName.equals(".") || visibleName.equals("..")) {
            throw new IllegalArgumentException("Invalid legacy skin name");
        }
        Path directory = cacheDirectory.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Path png = directory.resolve(visibleName + ".png");
        Path metadata = directory.resolve(visibleName + ".json");
        String geometry = standardGeometry(geometryPatch);
        boolean originalSlim = slim || "geometry.humanoid.customSlim".equals(geometry);
        Status status;
        if (persona) status = Status.UNSUPPORTED_PERSONA;
        else if (rgba == null || rgba.length == 0) status = Status.NO_SKIN;
        else if (!validDimensions(width, height)) status = Status.UNSUPPORTED_DIMENSIONS;
        else if (rgba.length > MAX_RGBA_BYTES || rgba.length != width * height * 4) status = Status.INVALID_RGBA;
        else if (geometry == null) status = Status.UNSUPPORTED_GEOMETRY;
        else status = Status.EXPORTED;

        if (status == Status.EXPORTED) {
            BufferedImage image = normalizedImage(rgba, width, height, originalSlim);
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "PNG", encoded)) throw new IOException("PNG encoder unavailable");
            writeAtomic(png, encoded.toByteArray());
        } else {
            // A previous conventional skin must not be advertised as the new
            // persona/custom skin. This is only a replaceable cache asset.
            Files.deleteIfExists(png);
        }
        int outputHeight = status == Status.EXPORTED ? (height == 32 ? 32 : 64) : 0;
        String json = "{\n  \"schemaVersion\": 1,\n  \"status\": \"" + status.name() + "\",\n"
            + "  \"sourceWidth\": " + width + ",\n  \"sourceHeight\": " + height + ",\n"
            + "  \"outputWidth\": " + (status == Status.EXPORTED ? 64 : 0) + ",\n"
            + "  \"outputHeight\": " + outputHeight + ",\n"
            + "  \"originalSlim\": " + originalSlim + ",\n"
            + "  \"convertedSlimToWide\": " + (status == Status.EXPORTED && originalSlim) + ",\n"
            + "  \"legacyFixConversionRequired\": " + (status == Status.EXPORTED) + ",\n"
            + "  \"geometryCheck\": \"standard_identifier_only\"\n}\n";
        writeAtomic(metadata, json.getBytes(StandardCharsets.UTF_8));
        return new Result(status, status == Status.EXPORTED ? png : null, metadata, originalSlim);
    }

    private static boolean validDimensions(int width, int height) {
        return width == 64 && (height == 32 || height == 64) || width == 128 && height == 128;
    }

    private static String standardGeometry(byte[] patch) {
        if (patch == null || patch.length == 0 || patch.length > MAX_GEOMETRY_NAME_BYTES) return null;
        String text = new String(patch, StandardCharsets.UTF_8).trim();
        if (text.equals("geometry.humanoid") || text.equals("geometry.humanoid.custom")
            || text.equals("geometry.humanoid.customSlim")) return text;
        Matcher match = STANDARD_PATCH.matcher(text);
        return match.matches() ? match.group(1) : null;
    }

    /** Bedrock's byte order is RGBA, not Java's ARGB integer byte order. */
    static BufferedImage normalizedImage(byte[] rgba, int width, int height, boolean slim) {
        if (!validDimensions(width, height) || rgba == null || rgba.length > MAX_RGBA_BYTES
            || rgba.length != width * height * 4) throw new IllegalArgumentException("Invalid skin pixels");
        int scale = width / 64;
        BufferedImage image = new BufferedImage(64, height / scale, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < 64; x++) {
                int index = ((y * scale) * width + x * scale) * 4;
                int argb = (rgba[index + 3] & 255) << 24 | (rgba[index] & 255) << 16
                    | (rgba[index + 1] & 255) << 8 | rgba[index + 2] & 255;
                image.setRGB(x, y, argb);
            }
        }
        if (slim) widenSlimArms(image);
        return image;
    }

    /**
     * Same UV adaptation as LegacyFix SkinUtils.alexToSteve; local override
     * images lack its SkinData.alex flag, so that particular step must be done
     * before they enter LegacyFix. Other conversion stays with LegacyFix.
     */
    private static void widenSlimArms(BufferedImage image) {
        copy(image, 47, 20, 7, 12, 48, 20);
        copy(image, 46, 20, 1, 12, 47, 20);
        copy(image, 52, 20, 3, 12, 53, 20);
        copy(image, 47, 16, 3, 4, 48, 16);
        copy(image, 46, 16, 1, 4, 47, 16);
        copy(image, 50, 16, 1, 4, 51, 16);
        if (image.getHeight() == 64) {
            copy(image, 47, 36, 7, 12, 48, 36);
            copy(image, 46, 36, 1, 12, 47, 36);
            copy(image, 52, 36, 3, 12, 53, 36);
            copy(image, 47, 32, 3, 4, 48, 32);
            copy(image, 46, 32, 1, 4, 47, 32);
            copy(image, 50, 32, 1, 4, 51, 32);
            copy(image, 52, 52, 10, 12, 53, 52);
            copy(image, 62, 52, 1, 12, 63, 52);
            copy(image, 55, 48, 3, 4, 57, 48);
            copy(image, 52, 48, 3, 4, 53, 48);
            copy(image, 57, 48, 1, 4, 56, 48);
            copy(image, 36, 52, 10, 12, 37, 52);
            copy(image, 46, 52, 1, 12, 47, 52);
            copy(image, 39, 48, 3, 4, 41, 48);
            copy(image, 36, 48, 3, 4, 37, 48);
            copy(image, 41, 48, 1, 4, 40, 48);
        }
    }

    private static void copy(BufferedImage image, int x, int y, int width, int height, int toX, int toY) {
        // Snapshot before writing: the source and destination can overlap.
        int[] pixels = image.getRGB(x, y, width, height, null, 0, width);
        image.setRGB(toX, toY, width, height, pixels, 0, width);
    }

    private static void writeAtomic(Path target, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), ".skin-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
