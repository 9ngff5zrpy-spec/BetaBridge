package cl.nostalgia.betabridge;

import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.inventory.Container;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.inventory.InventoryHolder;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.GeyserSessionAdapter;
import org.geysermc.geyser.translator.inventory.CraftingInventoryTranslator;
import org.geysermc.geyser.translator.protocol.java.inventory.JavaContainerSetSlotTranslator;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Supplies Beta's omitted SlotCrafting prediction for a single open 3x3 table.
 * The original server still validates every container click. This adapter never
 * sends a synthetic craft, grants an item, or changes Geyser's shared registry.
 * All internal contracts are deliberately pinned to Geyser 2.12.0 build 1262.
 */
final class BetaCraftingTableInventoryTranslator extends CraftingInventoryTranslator {
    private static final JavaContainerSetSlotTranslator OUTPUT_TRANSLATOR = new JavaContainerSetSlotTranslator();
    private final BetaCraftingRecipes recipes;
    private final InventoryHolder<?> holder;
    private int[] lastGrid;
    private boolean updating;

    private BetaCraftingTableInventoryTranslator(BetaCraftingRecipes recipes, InventoryHolder<?> holder) {
        this.recipes = recipes;
        this.holder = holder;
    }

    /** Register after Geyser's own adapter, so an OpenScreen has already created its holder. */
    static void register(GeyserSession session, BetaCraftingRecipes recipes, Consumer<String> reportFailure) {
        requirePinnedBuild();
        if (session.isClosed() || session.getDownstream() == null) {
            throw new IllegalStateException("No active downstream for the table adapter");
        }
        Session downstream = session.getDownstream().getSession();
        if (downstream.getListeners().stream().anyMatch(TableListener.class::isInstance)) return;
        if (downstream.getListeners().stream().noneMatch(GeyserSessionAdapter.class::isInstance)) {
            throw new IllegalStateException("The Geyser session adapter must run before the table adapter");
        }
        downstream.addListener(new TableListener(session, downstream, recipes, reportFailure));
    }

    private static void requirePinnedBuild() {
        if (!"1262".equals(GeyserImpl.BUILD_NUMBER)) {
            throw new IllegalStateException("Table adapter requires the pinned Geyser build 1262");
        }
    }

    private static void install(GeyserSession session, BetaCraftingRecipes recipes, int containerId)
            throws ReflectiveOperationException {
        requirePinnedBuild();
        if (!session.getTickEventLoop().inEventLoop()) {
            throw new IllegalStateException("Table adapter must run on the session event loop");
        }
        InventoryHolder<?> holder = session.getInventoryHolder();
        if (containerId <= 0 || holder == null || holder.javaId() != containerId
                || !(holder.inventory() instanceof Container)) {
            throw new IllegalStateException("The expected crafting table is not the current container");
        }
        if (holder.translator() instanceof BetaCraftingTableInventoryTranslator) return;
        if (holder.translator().getClass() != CraftingInventoryTranslator.class) {
            throw new IllegalStateException("A different table inventory translator is installed");
        }
        // Preserve the holder itself: pending-open tasks can retain references to it.
        Field translator = InventoryHolder.class.getDeclaredField("translator");
        translator.setAccessible(true);
        var original = holder.translator();
        var replacement = new BetaCraftingTableInventoryTranslator(recipes, holder);
        translator.set(holder, replacement);
        try {
            if (holder.translator() != replacement) {
                throw new IllegalStateException("Table inventory translator replacement failed");
            }
            replacement.refreshOutput(session, (Container) holder.inventory());
        } catch (RuntimeException e) {
            translator.set(holder, original);
            throw e;
        }
    }

    @Override
    public void updateInventory(GeyserSession session, Container inventory) {
        super.updateInventory(session, inventory);
        refreshOutput(session, inventory);
    }

    @Override
    public void updateSlot(GeyserSession session, Container inventory, int slot) {
        super.updateSlot(session, inventory, slot);
        if (slot >= 0 && slot <= 9) refreshOutput(session, inventory);
    }

    @Override
    public ItemStackResponse translateRequest(GeyserSession session, Container inventory, ItemStackRequest request) {
        refreshOutput(session, inventory);
        var drop = BetaDropRequests.prepare(session, inventory, this, request);
        if (drop != null) {
            if (drop.plan() == null) return rejectRequest(request);
            drop.plan().execute(false);
            refreshOutput(session, inventory);
            return acceptRequest(request, makeContainerEntries(session, inventory, drop.plan().getAffectedSlots()));
        }
        ItemStackResponse response = super.translateRequest(session, inventory, request);
        refreshOutput(session, inventory);
        return response;
    }

    @Override
    public ItemStackResponse translateCraftingRequest(GeyserSession session, Container inventory, ItemStackRequest request) {
        refreshOutput(session, inventory);
        ItemStackResponse response = super.translateCraftingRequest(session, inventory, request);
        refreshOutput(session, inventory);
        return response;
    }

    @Override
    public ItemStackResponse translateAutoCraftingRequest(GeyserSession session, Container inventory, ItemStackRequest request) {
        refreshOutput(session, inventory);
        ItemStackResponse response = super.translateAutoCraftingRequest(session, inventory, request);
        refreshOutput(session, inventory);
        return response;
    }

    private void refreshOutput(GeyserSession session, Container inventory) {
        if (updating || session.isClosed() || session.getInventoryHolder() != holder
                || holder.inventory() != inventory || inventory.getJavaId() <= 0) return;
        if (!session.getTickEventLoop().inEventLoop()) {
            throw new IllegalStateException("Table prediction must run on the session event loop");
        }
        var grid = new BetaCraftingRecipes.Stack[9];
        var fingerprint = new int[27];
        for (int i = 0; i < 9; i++) {
            GeyserItemStack stack = inventory.getItem(i + 1);
            grid[i] = BetaLegacyItems.ingredient(stack);
            fingerprint[i * 3] = stack.getJavaId();
            fingerprint[i * 3 + 1] = stack.getDamage();
            fingerprint[i * 3 + 2] = stack.getAmount();
        }
        BetaCraftingRecipes.Stack result = recipes.find(grid);
        Item item = BetaLegacyItems.output(result);
        // Unknown inputs remain occupied and unknown outputs predict nothing.
        ItemStack predicted = item == null ? null : new ItemStack(item.javaId(), result.count());
        GeyserItemStack existing = inventory.getItem(0);
        boolean sameOutput = predicted == null ? existing.isEmpty()
            : existing.getJavaId() == predicted.getId() && existing.getAmount() == predicted.getAmount();
        if (Arrays.equals(lastGrid, fingerprint) && sameOutput) return;
        lastGrid = fingerprint;
        updating = true;
        try {
            OUTPUT_TRANSLATOR.translate(session, new ClientboundContainerSetSlotPacket(
                inventory.getJavaId(), inventory.getStateId(), 0, predicted));
        } finally {
            updating = false;
        }
    }

    private static final class TableListener extends SessionAdapter {
        private final GeyserSession session;
        private final Session downstream;
        private final BetaCraftingRecipes recipes;
        private final Consumer<String> reportFailure;
        private boolean failed;

        private TableListener(GeyserSession session, Session downstream, BetaCraftingRecipes recipes,
                              Consumer<String> reportFailure) {
            this.session = session;
            this.downstream = downstream;
            this.recipes = recipes;
            this.reportFailure = reportFailure;
        }

        @Override
        public void packetReceived(Session source, Packet packet) {
            if (failed || session.isClosed() || source != downstream
                    || session.getDownstream() == null || session.getDownstream().getSession() != source
                    || !(packet instanceof ClientboundOpenScreenPacket screen)
                    || screen.getType() != ContainerType.CRAFTING) return;
            try {
                install(session, recipes, screen.getContainerId());
            } catch (ReflectiveOperationException | RuntimeException e) {
                failed = true;
                // The standard server-validated path remains; no alternate recipe or item is fabricated.
                reportFailure.accept("Crafteo Beta 3x3 desactivado para esta sesión: "
                    + e.getClass().getSimpleName());
            }
        }
    }
}
