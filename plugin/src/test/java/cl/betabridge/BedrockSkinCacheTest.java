/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.betabridge;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import javax.imageio.ImageIO;

/** PNG boundary tests; no Xbox data, running server, or client instance needed. */
public final class BedrockSkinCacheTest {
    private static final byte[] STANDARD = "{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}"
        .getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("betabridge-skin-test-");
        try {
            for (int height : new int[] {32, 64}) {
                byte[] pixels = pixels(64, height);
                var result = BedrockSkinCache.exportPixels(folder, ".Test_Player", pixels, 64, height,
                    false, false, STANDARD);
                check(result.exported(), "standard skin exported");
                BufferedImage image = ImageIO.read(result.png().toFile());
                check(image.getWidth() == 64 && image.getHeight() == height, "valid PNG dimensions");
                for (int y = 0; y < height; y++) for (int x = 0; x < 64; x++) {
                    check(image.getRGB(x, y) == expectedPixel(x, y), "RGBA, alpha and UV preserved");
                }
                check(!Files.readString(result.metadata()).contains("xuid"), "metadata contains no identity tokens");
            }
            var high = BedrockSkinCache.exportPixels(folder, ".Test_Player", pixels(128, 128), 128, 128,
                false, false, STANDARD);
            BufferedImage downsampled = ImageIO.read(high.png().toFile());
            check(downsampled.getWidth() == 64 && downsampled.getHeight() == 64, "HD skin normalized");
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                check(downsampled.getRGB(x, y) == expectedPixel(x * 2, y * 2), "nearest pixel HD normalization");
            }
            BufferedImage slim = BedrockSkinCache.normalizedImage(pixels(64, 64), 64, 64, true);
            check(slim.getRGB(47, 23) == expectedPixel(46, 23), "slim right arm widened");
            check(slim.getRGB(48, 23) == expectedPixel(47, 23), "overlapping right UV copied safely");
            check(slim.getRGB(37, 57) == expectedPixel(36, 57), "slim left arm widened");
            check(slim.getRGB(15, 15) == expectedPixel(15, 15), "face remains unchanged");
            var inferred = BedrockSkinCache.exportPixels(folder, ".Test_Player", pixels(64, 64), 64, 64,
                false, false, "geometry.humanoid.customSlim".getBytes(StandardCharsets.UTF_8));
            check(inferred.exported() && inferred.originalSlim(), "geometry supplies slim flag");

            var persona = BedrockSkinCache.exportPixels(folder, ".Test_Player", pixels(64, 64), 64, 64,
                true, false, STANDARD);
            check(persona.status() == BedrockSkinCache.Status.UNSUPPORTED_PERSONA, "persona is explicit fallback");
            check(!Files.exists(folder.resolve(".Test_Player.png")), "stale conventional skin removed on fallback");
            check(Files.readString(persona.metadata()).contains("UNSUPPORTED_PERSONA"), "fallback recorded");
            check(BedrockSkinCache.exportPixels(folder, ".NoSkin", null, 0, 0, false, false, null).status()
                == BedrockSkinCache.Status.NO_SKIN, "missing pixels fallback");
            check(BedrockSkinCache.exportPixels(folder, ".BadSize", new byte[4], 1, 1, false, false, STANDARD).status()
                == BedrockSkinCache.Status.UNSUPPORTED_DIMENSIONS, "unsupported dimensions fallback");
            check(BedrockSkinCache.exportPixels(folder, ".BadPixels", new byte[1], 64, 64, false, false, STANDARD).status()
                == BedrockSkinCache.Status.INVALID_RGBA, "truncated pixels fallback");
            for (byte[] geometry : new byte[][] {null, new byte[8193], "geometry.dragon".getBytes(StandardCharsets.UTF_8),
                    "{\"geometry\":{\"default\":\"geometry.humanoid.custom\"},\"extra\":true}".getBytes(StandardCharsets.UTF_8)}) {
                check(BedrockSkinCache.exportPixels(folder, ".BadModel", pixels(64, 64), 64, 64,
                    false, false, geometry).status() == BedrockSkinCache.Status.UNSUPPORTED_GEOMETRY,
                    "unknown model cannot masquerade as a classic skin");
            }
            for (String name : new String[] {null, "", ".", "..", "../escape", "a/b", "a\\b", "over16charactersxx", "\n"}) {
                try {
                    BedrockSkinCache.exportPixels(folder, name, pixels(64, 64), 64, 64, false, false, STANDARD);
                    throw new AssertionError("unsafe filename accepted");
                } catch (IllegalArgumentException expected) { }
            }
            try (var files = Files.list(folder)) {
                check(files.noneMatch(path -> path.getFileName().toString().startsWith(".skin-")), "atomic writes leave no temporary file");
            }
            System.out.println("BedrockSkinCacheTest passed: PNG pixels/alpha, HD, slim UV, fallback, filename and atomic-write boundaries.");
        } finally {
            try (var paths = Files.walk(folder)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static byte[] pixels(int width, int height) {
        byte[] rgba = new byte[width * height * 4];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int index = (y * width + x) * 4;
            int color = expectedPixel(x, y);
            rgba[index] = (byte) (color >> 16);
            rgba[index + 1] = (byte) (color >> 8);
            rgba[index + 2] = (byte) color;
            rgba[index + 3] = (byte) (color >>> 24);
        }
        return rgba;
    }

    private static int expectedPixel(int x, int y) {
        return ((x + y + 17) & 255) << 24 | (x & 255) << 16 | (y & 255) << 8 | ((x ^ y) & 255);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
