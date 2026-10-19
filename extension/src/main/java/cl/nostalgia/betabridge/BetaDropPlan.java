package cl.nostalgia.betabridge;

import java.util.ArrayList;
import java.util.List;

/** Beta supports dropping the cursor outside, not modern slot-drop mode. */
public final class BetaDropPlan {
    public record Step(int slot, int button) { }

    public static List<Step> steps(int sourceSlot, int sourceCount, int dropCount) {
        if (sourceSlot < 1 || sourceCount < 1 || sourceCount > 64 || dropCount < 1 || dropCount > sourceCount) {
            throw new IllegalArgumentException("Invalid Beta drop quantity or source");
        }
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(sourceSlot, 0));
        if (dropCount == sourceCount) {
            steps.add(new Step(-999, 0));
        } else {
            for (int i = 0; i < dropCount; i++) steps.add(new Step(-999, 1));
            steps.add(new Step(sourceSlot, 0));
        }
        return List.copyOf(steps);
    }
}
