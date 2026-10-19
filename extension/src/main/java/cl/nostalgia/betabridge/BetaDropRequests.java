package cl.nostalgia.betabridge;

import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DropAction;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.inventory.SlotType;
import org.geysermc.geyser.inventory.click.Click;
import org.geysermc.geyser.inventory.click.ClickPlan;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;

/** Narrow adapter for plain inventory drops; crafting and cursor drops stay upstream. */
final class BetaDropRequests {
    record Prepared(ClickPlan plan) { }

    static <T extends Inventory> Prepared prepare(GeyserSession session, T inventory,
                                                  InventoryTranslator<T> translator, ItemStackRequest request) {
        if (request.getActions().length == 0) return null;
        for (var action : request.getActions()) {
            if (!(action instanceof DropAction drop)
                || drop.getSource().getContainerName().getContainer() == ContainerSlotType.CURSOR) return null;
            int slot = translator.bedrockSlotToJava(drop.getSource());
            if (slot >= 0 && slot < inventory.getSize() && translator.getSlotType(slot) != SlotType.NORMAL) return null;
        }
        if (!session.getPlayerInventory().getCursor().isEmpty()) return new Prepared(null);
        ClickPlan plan = new ClickPlan(session, translator, inventory);
        for (var action : request.getActions()) {
            DropAction drop = (DropAction) action;
            int source = translator.bedrockSlotToJava(drop.getSource());
            if (source < 1 || source >= inventory.getSize() || !translator.checkNetId(session, inventory, drop.getSource())) {
                return new Prepared(null);
            }
            int amount = plan.getItem(source).getAmount();
            if (amount < 1 || amount > 64 || drop.getCount() < 1 || drop.getCount() > amount) return new Prepared(null);
            for (BetaDropPlan.Step step : BetaDropPlan.steps(source, amount, drop.getCount())) {
                Click click = step.slot() == -999
                    ? (step.button() == 0 ? Click.LEFT_OUTSIDE : Click.RIGHT_OUTSIDE) : Click.LEFT;
                plan.add(click, step.slot());
            }
        }
        return new Prepared(plan);
    }
}
