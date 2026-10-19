/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.nostalgia.betabridge;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.minecraft.item.DataItem;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import net.raphimc.vialegacy.protocol.beta.b1_4_0_1tob1_5_0_2.packet.ServerboundPacketsb1_4;
import net.raphimc.vialegacy.protocol.release.r1_4_2tor1_4_4_5.types.Types1_4_2;
import net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.types.Types1_7_6;

/** Two small Beta actions; world, inventory and mount decisions remain server-side. */
public final class BetaLegacyActions {
    private static com.viaversion.viaversion.api.protocol.packet.ServerboundPacketType packet(String name) {
        return BetaCraftingRecipes.beta166()
            ? net.raphimc.vialegacy.protocol.beta.b1_7_0_3tob1_8_0_1.packet.ServerboundPacketsb1_7.valueOf(name)
            : ServerboundPacketsb1_4.valueOf(name);
    }
    public static PacketWrapper select(UserConnection user, int slot) {
        if (slot < 0 || slot > 8) throw new IllegalArgumentException("hotbar slot");
        var packet = PacketWrapper.create(packet("SET_CARRIED_ITEM"), user);
        packet.write(Types.SHORT, (short) slot);
        return packet;
    }

    public static PacketWrapper jukebox(UserConnection user, int x, int y, int z, int face, int record, int count) {
        if (y < 0 || y >= 128 || face < 0 || face > 5 || (record != 0 && record != 2256 && record != 2257)
                || count < 0 || count > 64) throw new IllegalArgumentException("Beta block interaction");
        var packet = PacketWrapper.create(packet("USE_ITEM_ON"), user);
        packet.write(Types1_7_6.BLOCK_POSITION_UBYTE, new BlockPosition(x, y, z));
        packet.write(Types.UNSIGNED_BYTE, (short) face);
        // Beta uses its actual selected stack. This field only compares the
        // client's pre-click witness with the result to request a slot refresh.
        packet.write(Types1_4_2.NBTLESS_ITEM, record == 0 ? null : new DataItem(record, (byte) count, (short) 0, null));
        return packet;
    }

    /** One classic block interaction; Beta validates distance, permissions and state. */
    public static PacketWrapper control(UserConnection user,int x,int y,int z,int face) {
        if(y<0||y>=128||face<0||face>5)throw new IllegalArgumentException("Beta control position");
        var packet=PacketWrapper.create(packet("USE_ITEM_ON"),user);
        packet.write(Types1_7_6.BLOCK_POSITION_UBYTE,new BlockPosition(x,y,z));
        packet.write(Types.UNSIGNED_BYTE,(short)face);
        // Witness only, never an invented stack. A nonempty held stack receives
        // the server's ordinary SetSlot refresh; controls do not consume it.
        packet.write(Types1_4_2.NBTLESS_ITEM,null);
        return packet;
    }

    /** Beta has no yaw/pitch in use-item; send touch aim as a separate ordered look. */
    public static PacketWrapper look(UserConnection user, float yaw, float pitch, boolean onGround) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90 || pitch > 90)
            throw new IllegalArgumentException("invalid touch aim");
        var packet = PacketWrapper.create(packet("MOVE_PLAYER_ROT"), user);
        packet.write(Types.FLOAT, yaw);
        packet.write(Types.FLOAT, pitch);
        packet.write(Types.BOOLEAN, onGround);
        return packet;
    }

    public static PacketWrapper boat(UserConnection user, int count) {
        if (count < 1 || count > 64) throw new IllegalArgumentException("boat count");
        var packet = PacketWrapper.create(packet("USE_ITEM_ON"), user);
        packet.write(Types1_7_6.BLOCK_POSITION_UBYTE, new BlockPosition(-1, 255, -1));
        packet.write(Types.UNSIGNED_BYTE, (short) 255);
        packet.write(Types1_4_2.NBTLESS_ITEM, new DataItem(333, (byte) count, (short) 0, null));
        return packet;
    }

    public static PacketWrapper dismount(UserConnection user, int player, int boat) {
        var packet = PacketWrapper.create(packet("INTERACT"), user);
        packet.write(Types.INT, player);
        packet.write(Types.INT, boat);
        packet.write(Types.BYTE, (byte) 0); // Beta right-clicks the same boat to leave it.
        return packet;
    }
    /** Original Beta mounted-player horizontal motion, not a vehicle teleport. */
    public static PacketWrapper riderMotion(UserConnection user,double vx,double vz,float yaw,float pitch){
        if(!Double.isFinite(vx)||!Double.isFinite(vz)||Math.abs(vx)>.25||Math.abs(vz)>.25
                ||!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90)throw new IllegalArgumentException("Beta rider input");
        var packet=PacketWrapper.create(packet("MOVE_PLAYER_POS_ROT"),user);
        packet.write(Types.DOUBLE,vx);packet.write(Types.DOUBLE,-999d);
        packet.write(Types.DOUBLE,-999d);packet.write(Types.DOUBLE,vz);
        packet.write(Types.FLOAT,yaw);packet.write(Types.FLOAT,pitch);packet.write(Types.BOOLEAN,false);
        return packet;
    }
}
