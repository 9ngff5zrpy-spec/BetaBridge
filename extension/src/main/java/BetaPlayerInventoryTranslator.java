package cl.nostalgia.betabridge;

import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.inventory.InventoryHolder;
import org.geysermc.geyser.inventory.PlayerInventory;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.inventory.PlayerInventoryTranslator;
import org.geysermc.geyser.translator.protocol.java.inventory.JavaContainerSetSlotTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Beta skips SlotCrafting updates because its Java client calculates that slot locally.
 * Supply only the prediction that Geyser needs; every actual craft is still a normal
 * container click validated by the original Beta server. No inventory items are granted.
 *
 * Uses Geyser internals pinned to 2.12.0-b1262. The private final instance field is
 * replaced per connection; no shared static translator or global registry is changed.
 */
final class BetaPlayerInventoryTranslator extends PlayerInventoryTranslator {
    private static final JavaContainerSetSlotTranslator OUTPUT_TRANSLATOR = new JavaContainerSetSlotTranslator();
    private final BetaCraftingRecipes recipes;
    private int[] lastGrid;
    private boolean updating;

    private BetaPlayerInventoryTranslator(BetaCraftingRecipes recipes) {
        this.recipes = recipes;
    }

    static void install(GeyserSession session, BetaCraftingRecipes recipes) throws ReflectiveOperationException {
        if (!"1262".equals(GeyserImpl.BUILD_NUMBER)) {
            throw new IllegalStateException("Crafting adapter requires the pinned Geyser build 1262");
        }
        InventoryHolder<PlayerInventory> holder = session.getPlayerInventoryHolder();
        if (holder.translator() instanceof BetaPlayerInventoryTranslator) return;
        if (holder.translator().getClass() != PlayerInventoryTranslator.class) {
            throw new IllegalStateException("A different player inventory translator is installed");
        }
        Field translator = InventoryHolder.class.getDeclaredField("translator");
        translator.setAccessible(true);
        var original = holder.translator();
        var replacement = new BetaPlayerInventoryTranslator(recipes);
        translator.set(holder, replacement);
        try {
            if (holder.translator() != replacement) throw new IllegalStateException("Inventory translator replacement failed");
            replacement.refreshOutput(session, holder.inventory());
        } catch (RuntimeException e) {
            translator.set(holder, original);
            throw e;
        }
    }

    @Override
    public void updateInventory(GeyserSession session, PlayerInventory inventory) {
        super.updateInventory(session, inventory);
        refreshOutput(session, inventory);
    }

    @Override
    public void updateSlot(GeyserSession session, PlayerInventory inventory, int slot) {
        super.updateSlot(session, inventory, slot);
        if (slot >= 0 && slot <= 4) refreshOutput(session, inventory);
    }

    @Override
    public ItemStackResponse translateRequest(GeyserSession session, PlayerInventory inventory, ItemStackRequest request) {
        refreshOutput(session, inventory);
        var drop = BetaDropRequests.prepare(session, inventory, this, request);
        if (drop != null) {
            if (drop.plan() == null) return rejectRequest(request);
            drop.plan().execute(false);
            refreshOutput(session, inventory);
            session.getGeyser().getLogger().info("Beta 1.4_01: solicitud de soltar objetos adaptada a clicks de cursor.");
            return acceptRequest(request, makeContainerEntries(session, inventory, drop.plan().getAffectedSlots()));
        }
        ItemStackResponse response = super.translateRequest(session, inventory, request);
        refreshOutput(session, inventory);
        return response;
    }

    @Override
    public ItemStackResponse translateCraftingRequest(GeyserSession session, PlayerInventory inventory, ItemStackRequest request) {
        refreshOutput(session, inventory);
        ItemStackResponse response = super.translateCraftingRequest(session, inventory, request);
        refreshOutput(session, inventory);
        return response;
    }

    @Override
    public ItemStackResponse translateAutoCraftingRequest(GeyserSession session, PlayerInventory inventory, ItemStackRequest request) {
        ItemStackResponse response = super.translateAutoCraftingRequest(session, inventory, request);
        refreshOutput(session, inventory);
        return response;
    }

    private void refreshOutput(GeyserSession session, PlayerInventory inventory) {
        if (updating || session.isClosed()) return;
        var grid = new BetaCraftingRecipes.Stack[4];
        var fingerprint = new int[12];
        for (int i = 0; i < 4; i++) {
            GeyserItemStack item = inventory.getItem(i + 1);
            grid[i] = BetaLegacyItems.ingredient(item);
            fingerprint[i * 3] = item.getJavaId();
            fingerprint[i * 3 + 1] = item.getDamage();
            fingerprint[i * 3 + 2] = item.getAmount();
        }
        BetaCraftingRecipes.Stack result = recipes.find(grid);
        Item item = BetaLegacyItems.output(result);
        ItemStack predicted = item == null ? null : new ItemStack(item.javaId(), result.count());
        GeyserItemStack existing = inventory.getItem(0);
        boolean sameOutput = predicted == null ? existing.isEmpty()
            : existing.getJavaId() == predicted.getId() && existing.getAmount() == predicted.getAmount();
        if (Arrays.equals(lastGrid, fingerprint) && sameOutput) return;
        lastGrid = fingerprint;
        updating = true;
        try {
            // Reuse Geyser's recipe discovery and normal output-slot cache update.
            // This is clientbound only; it never sends a synthetic craft to Beta.
            OUTPUT_TRANSLATOR.translate(session,
                new ClientboundContainerSetSlotPacket(0, inventory.getStateId(), 0, predicted));
        } finally {
            updating = false;
        }
    }
}
