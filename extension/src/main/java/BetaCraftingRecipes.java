package cl.nostalgia.betabridge;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Pure recipe matching; the catalog is extracted from the pinned original Beta server. */
public final class BetaCraftingRecipes {
    public record Stack(int id, @SerializedName("meta") int metadata, int count) { }
    public record Recipe(String type, int width, int height, List<Stack> ingredients,
                         @SerializedName("output") Stack result) {
        public boolean shaped() { return "shaped".equals(type); }
    }
    public record Catalog(List<Recipe> recipes) { }

    private final List<Recipe> recipes;

    public BetaCraftingRecipes(List<Recipe> recipes) {
        this.recipes = List.copyOf(recipes);
    }

    public int size() { return recipes.size(); }

    Catalog catalog() { return new Catalog(recipes); }

    static boolean beta166() { return "b1.6.6".equals(System.getProperty("betabridge.profile", "b1.4_01")); }

    public static BetaCraftingRecipes load() throws IOException {
        return beta166() ? load("/beta-1.6.6-recipes.json", 56) : load("/beta-1.4_01-recipes.json", 54);
    }

    /** The complete catalog is kept separate from the original personal-inventory subset. */
    public static BetaCraftingRecipes loadTable() throws IOException {
        return beta166() ? load("/beta-1.6.6-all-recipes.json", 148) : load("/beta-1.4_01-all-recipes.json", 144);
    }

    private static BetaCraftingRecipes load(String resource, int expectedCount) throws IOException {
        var stream = BetaCraftingRecipes.class.getResourceAsStream(resource);
        if (stream == null) throw new IOException("Missing original Beta recipe catalog");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Catalog catalog = new Gson().fromJson(reader, Catalog.class);
            if (catalog == null || catalog.recipes() == null || catalog.recipes().size() != expectedCount) {
                throw new IOException("Invalid original Beta recipe catalog");
            }
            return new BetaCraftingRecipes(catalog.recipes());
        }
    }

    /** Null means either an empty slot or no matching recipe. Counts never substitute for extra slots. */
    public Stack find(Stack[] grid) {
        int dimension = switch (grid.length) {
            case 4 -> 2;
            case 9 -> 3;
            default -> throw new IllegalArgumentException("Expected a 2x2 or 3x3 crafting grid");
        };
        for (Recipe recipe : recipes) {
            if (recipe.shaped()) {
                if (recipe.width() > dimension || recipe.height() > dimension) continue;
                for (int x = 0; x <= dimension - recipe.width(); x++) {
                    for (int y = 0; y <= dimension - recipe.height(); y++) {
                        if (matchesShape(recipe, grid, dimension, x, y, false)
                            || matchesShape(recipe, grid, dimension, x, y, true)) return recipe.result();
                    }
                }
            } else if (matchesShapeless(recipe, grid)) {
                return recipe.result();
            }
        }
        return null;
    }

    private static boolean matchesShape(Recipe recipe, Stack[] grid, int dimension,
                                        int offsetX, int offsetY, boolean mirror) {
        for (int y = 0; y < dimension; y++) {
            for (int x = 0; x < dimension; x++) {
                int rx = x - offsetX, ry = y - offsetY;
                Stack expected = null;
                if (rx >= 0 && rx < recipe.width() && ry >= 0 && ry < recipe.height()) {
                    int index = (mirror ? recipe.width() - rx - 1 : rx) + ry * recipe.width();
                    expected = recipe.ingredients().get(index);
                }
                if (!matches(expected, grid[x + y * dimension])) return false;
            }
        }
        return true;
    }

    private static boolean matchesShapeless(Recipe recipe, Stack[] grid) {
        List<Stack> remaining = new ArrayList<>(recipe.ingredients());
        for (Stack actual : grid) {
            if (isEmpty(actual)) continue;
            int index = -1;
            for (int i = 0; i < remaining.size(); i++) {
                if (matches(remaining.get(i), actual)) { index = i; break; }
            }
            if (index == -1) return false;
            remaining.remove(index);
        }
        return remaining.isEmpty();
    }

    private static boolean matches(Stack expected, Stack actual) {
        if (isEmpty(expected)) return isEmpty(actual);
        return !isEmpty(actual) && expected.id() == actual.id()
            && (expected.metadata() == -1 || expected.metadata() == actual.metadata());
    }

    private static boolean isEmpty(Stack stack) {
        return stack == null || stack.id() <= 0 || stack.count() <= 0;
    }
}
