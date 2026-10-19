/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.betabridge;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Inflater;
import java.util.zip.DataFormatException;

/** Observes already-framed original Beta block updates; never rewrites packets. */
final class BetaJukeboxPackets {
    record Change(int x, int y, int z, int block, int metadata) { }

    static List<Change> read(ByteBuf buffer) throws DataFormatException {
        int start = buffer.readerIndex(), size = buffer.readableBytes();
        if (size == 0) return List.of();
        int packet = buffer.getUnsignedByte(start);
        List<Change> result = new ArrayList<>();
        if (packet == 53 && size == 12) {
            result.add(new Change(buffer.getInt(start + 1), buffer.getUnsignedByte(start + 5),
                buffer.getInt(start + 6), buffer.getUnsignedByte(start + 10), buffer.getUnsignedByte(start + 11) & 15));
        } else if (packet == 52 && size >= 11) {
            int x = buffer.getInt(start + 1) << 4, z = buffer.getInt(start + 5) << 4;
            int count = buffer.getUnsignedShort(start + 9);
            if (size != 11 + count * 4) return List.of();
            for (int i = 0; i < count; i++) {
                int position = buffer.getUnsignedShort(start + 11 + i * 2);
                result.add(new Change(x + (position >>> 12), position & 255, z + ((position >>> 8) & 15),
                    buffer.getUnsignedByte(start + 11 + count * 2 + i),
                    buffer.getUnsignedByte(start + 11 + count * 3 + i) & 15));
            }
        } else if (packet == 51 && size >= 18) {
            int x = buffer.getInt(start + 1), y = buffer.getShort(start + 5), z = buffer.getInt(start + 7);
            int sx = buffer.getUnsignedByte(start + 11) + 1;
            int sy = buffer.getUnsignedByte(start + 12) + 1;
            int sz = buffer.getUnsignedByte(start + 13) + 1;
            int compressed = buffer.getInt(start + 14);
            if (sx > 16 || sy > 128 || sz > 16 || compressed < 0 || compressed > 1048576 || size != compressed + 18) return List.of();
            int volume = sx * sy * sz;
            if ((volume & 1) != 0) return List.of();
            byte[] encoded = new byte[compressed], decoded = new byte[volume * 5 / 2];
            buffer.getBytes(start + 18, encoded);
            Inflater inflater = new Inflater();
            try {
                inflater.setInput(encoded);
                int total = 0;
                while (total < decoded.length && !inflater.finished()) {
                    int read = inflater.inflate(decoded, total, decoded.length - total);
                    if (read == 0) return List.of();
                    total += read;
                }
                if (total != decoded.length || !inflater.finished()) return List.of();
            } finally { inflater.end(); }
            for (int i = 0; i < volume; i++) {
                if ((decoded[i] & 255) != 84) continue;
                int metadata = (decoded[volume + i / 2] >>> ((i & 1) * 4)) & 15;
                result.add(new Change(x + i / (sy * sz), y + i % sy, z + (i / sy) % sz, 84, metadata));
            }
        }
        return List.copyOf(result);
    }
}
