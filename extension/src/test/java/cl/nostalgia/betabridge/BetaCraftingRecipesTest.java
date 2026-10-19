package cl.nostalgia.betabridge;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executors;

/** Checks our matcher against the original server's headless CraftingManager, never a live world. */
public final class BetaCraftingRecipesTest {
    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]);
        var personal = BetaCraftingRecipes.load();
        var table = BetaCraftingRecipes.loadTable();
        verifyProfile(personal, 2, BetaCraftingRecipes.beta166()?56:54, jar);
        verifyProfile(table, 3, BetaCraftingRecipes.beta166()?148:144, jar);
        require(personal.size() == (BetaCraftingRecipes.beta166()?56:54), "The personal-inventory subset must remain separate");
        require(table.size() == (BetaCraftingRecipes.beta166()?148:144), "The table must retain the complete original catalog");
        require(personal.find(new BetaCraftingRecipes.Stack[]{new BetaCraftingRecipes.Stack(17, 0, 1), null, null, null})
            .equals(new BetaCraftingRecipes.Stack(5, 0, 4)), "Log -> planks");
        require(personal.find(new BetaCraftingRecipes.Stack[]{new BetaCraftingRecipes.Stack(5, 0, 2), null, null, null}) == null,
            "One occupied slot must not substitute for a two-slot stick recipe");
        var compressedPlanks = new BetaCraftingRecipes.Stack[9];
        compressedPlanks[4] = new BetaCraftingRecipes.Stack(5, 0, 64);
        require(table.find(compressedPlanks) == null, "Counts must not replace a 3x3 recipe's occupied slots");
        for (int invalidSize : new int[]{0, 1, 6, 8, 10}) {
            try {
                table.find(new BetaCraftingRecipes.Stack[invalidSize]);
                throw new AssertionError("Unsupported grid size was accepted: " + invalidSize);
            } catch (IllegalArgumentException expected) { }
        }
    }

    private static void verifyProfile(BetaCraftingRecipes matcher, int dimension, int recipeCount, Path jar)
            throws Exception {
        var catalog = matcher.catalog();
        require(catalog.recipes().size() == recipeCount, "Unexpected catalog size");
        int gridSize = dimension * dimension;
        List<BetaCraftingRecipes.Stack[]> cases = new ArrayList<>();
        cases.add(new BetaCraftingRecipes.Stack[gridSize]);
        var unsupported = new BetaCraftingRecipes.Stack[gridSize];
        unsupported[gridSize - 1] = new BetaCraftingRecipes.Stack(999, 0, 64);
        cases.add(unsupported);
        for (var recipe : catalog.recipes()) {
            if (recipe.shaped()) {
                require(recipe.width() <= dimension && recipe.height() <= dimension, "Recipe exceeds profile grid");
                for (int x = 0; x <= dimension - recipe.width(); x++) {
                    for (int y = 0; y <= dimension - recipe.height(); y++) {
                        for (boolean mirror : new boolean[]{false, true}) {
                            var grid = new BetaCraftingRecipes.Stack[gridSize];
                            for (int ry = 0; ry < recipe.height(); ry++) {
                                for (int rx = 0; rx < recipe.width(); rx++) {
                                    var input = recipe.ingredients().get((mirror ? recipe.width() - rx - 1 : rx)
                                        + ry * recipe.width());
                                    if (input != null) grid[x + rx + (y + ry) * dimension] = concrete(input, 64);
                                }
                            }
                            addVariants(cases, grid);
                        }
                    }
                }
            } else {
                for (int offset = 0; offset < gridSize; offset++) {
                    var grid = new BetaCraftingRecipes.Stack[gridSize];
                    for (int i = 0; i < recipe.ingredients().size(); i++) {
                        grid[(offset + gridSize - i) % gridSize] = concrete(recipe.ingredients().get(i), 64);
                    }
                    addVariants(cases, grid);
                }
            }
        }
        Random random = new Random(1401 + dimension);
        int[] ids = {4, 5, 17, 51, 263, 264, 265, 266, 280, 318, 338, 351, 352, 999};
        for (int n = 0; n < 1000; n++) {
            var grid = new BetaCraftingRecipes.Stack[gridSize];
            for (int i = 0; i < gridSize; i++) {
                if (random.nextBoolean()) grid[i] = new BetaCraftingRecipes.Stack(ids[random.nextInt(ids.length)],
                    random.nextInt(16), 1 + random.nextInt(64));
            }
            cases.add(grid);
        }
        List<BetaCraftingRecipes.Stack> expected = new ArrayList<>();
        try (Oracle oracle = new Oracle(jar)) {
            for (var grid : cases) {
                var actual = matcher.find(grid);
                var original = oracle.craft(grid);
                require(Objects.equals(actual, original), "Mismatch against original Beta " + dimension
                    + "x" + dimension + " for " + Arrays.toString(grid) + ": " + actual + " != " + original);
                expected.add(original);
            }
        }
        try (var threads = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<java.util.concurrent.Callable<Void>>();
            for (int iteration = 0; iteration < 8; iteration++) {
                tasks.add(() -> {
                    for (int i = 0; i < cases.size(); i++) {
                        require(Objects.equals(matcher.find(cases.get(i)), expected.get(i)), "Shared matcher state");
                    }
                    return null;
                });
            }
            for (var future : threads.invokeAll(tasks)) future.get();
        }
        System.out.println("PASS: " + recipeCount + " authentic " + dimension + "x" + dimension + " recipes; " + cases.size()
            + " grids agree with the original Beta oracle, including mirrors, offsets, counts, metadata and invalid recipes; 8 concurrent passes.");
    }

    /** Valid, missing, extra, wrong item and metadata/count variants are all checked against Beta itself. */
    private static void addVariants(List<BetaCraftingRecipes.Stack[]> cases, BetaCraftingRecipes.Stack[] grid) {
        cases.add(grid);
        int firstOccupied = -1, firstEmpty = -1;
        for (int i = 0; i < grid.length; i++) {
            if (grid[i] == null && firstEmpty == -1) firstEmpty = i;
            if (grid[i] != null && firstOccupied == -1) firstOccupied = i;
        }
        if (firstOccupied >= 0) {
            var missing = grid.clone();
            missing[firstOccupied] = null;
            cases.add(missing);
            var wrong = grid.clone();
            wrong[firstOccupied] = new BetaCraftingRecipes.Stack(999, 0, 1);
            cases.add(wrong);
            var counts = grid.clone();
            for (int i = 0; i < grid.length; i++) {
                if (grid[i] != null) counts[i] = new BetaCraftingRecipes.Stack(grid[i].id(), grid[i].metadata(), 1);
            }
            cases.add(counts);
            for (int metadata : new int[]{1, 2, 15}) {
                var variant = grid.clone();
                var item = variant[firstOccupied];
                variant[firstOccupied] = new BetaCraftingRecipes.Stack(item.id(), metadata, item.count());
                cases.add(variant);
            }
        }
        if (firstEmpty >= 0) {
            var extra = grid.clone();
            extra[firstEmpty] = new BetaCraftingRecipes.Stack(999, 0, 1);
            cases.add(extra);
        }
    }

    private static BetaCraftingRecipes.Stack concrete(BetaCraftingRecipes.Stack input, int count) {
        return new BetaCraftingRecipes.Stack(input.id(), Math.max(0, input.metadata()), count);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Oracle implements AutoCloseable {
        private final URLClassLoader loader;
        private final Object manager;
        private final Class<?> itemType;
        private final Constructor<?> gridConstructor;
        private final Constructor<?> itemConstructor;
        private final Method craft;
        private final Field contents;

        Oracle(Path jar) throws Exception {
            loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
            PrintStream out = System.out;
            try {
                System.setOut(new PrintStream(OutputStream.nullOutputStream()));
                Class.forName((BetaCraftingRecipes.beta166()?"mu":"lq"), true, loader);
                manager = Class.forName((BetaCraftingRecipes.beta166()?"ew":"ei"), true, loader).getMethod("a").invoke(null);
            } finally { System.setOut(out); }
            Class<?> gridType = loader.loadClass((BetaCraftingRecipes.beta166()?"hw":"he"));
            itemType = loader.loadClass((BetaCraftingRecipes.beta166()?"fw":"fg"));
            gridConstructor = gridType.getConstructor(loader.loadClass((BetaCraftingRecipes.beta166()?"cj":"cb")), int.class, int.class);
            itemConstructor = itemType.getConstructor(int.class, int.class, int.class);
            craft = manager.getClass().getMethod("a", gridType);
            contents = gridType.getDeclaredField("a");
            contents.setAccessible(true);
        }

        BetaCraftingRecipes.Stack craft(BetaCraftingRecipes.Stack[] input) throws Exception {
            int dimension = input.length == 4 ? 2 : 3;
            Object grid = gridConstructor.newInstance(null, dimension, dimension);
            Object items = Array.newInstance(itemType, input.length);
            for (int i = 0; i < input.length; i++) {
                var stack = input[i];
                if (stack != null) Array.set(items, i, itemConstructor.newInstance(stack.id(), stack.count(), stack.metadata()));
            }
            contents.set(grid, items);
            Object result = craft.invoke(manager, grid);
            if (result == null) return null;
            return new BetaCraftingRecipes.Stack(field(result, "c"), field(result, "d"), field(result, "a"));
        }

        private int field(Object item, String name) throws Exception {
            Field field = itemType.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(item);
        }

        @Override public void close() throws Exception { loader.close(); }
    }
}
