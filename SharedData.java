import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;

public class SharedData {
    // --- Configuration ---
    public static final int MAP_SIZE = 1024;
    public static final int TILE_SIZE = 32;
    public static final int PORT = 25565;
    public static final long MAP_SEED = 12345;

    // --- Block IDs ---
    public static final int AIR = 0;
    public static final int BEDROCK = 1;
    public static final int DIRT = 2;
    public static final int STONE = 3;
    public static final int WOOD = 4;
    public static final int PLANKS = 5;
    public static final int CRAFTER = 6;
    public static final int IRON_ORE = 7;
    public static final int GOLD_ORE = 8;
    public static final int DIAMOND_ORE = 9;
    public static final int FURNACE = 10;
    public static final int CHEST = 11;
    public static final int HULCS = 12;

    public static final Color[] PLAYER_COLORS = {
        Color.decode("#FF0000"), // Red
        Color.decode("#0000FF"), // Blue
        Color.decode("#00FF00"), // Green
        Color.decode("#FFFF00"), // Yellow
        Color.decode("#FF7F00"), // Orange
        Color.decode("#800080"), // Purple
        Color.decode("#00FFFF"), // Cyan
        Color.decode("#FF00FF"), // Pink
        Color.decode("#BFFF00"), // Lime
        Color.decode("#8B4513")  // Brown
    };

    public static final String[] COLOR_NAMES = {
        "Red", "Blue", "Green", "Yellow", "Orange", "Purple", "Cyan", "Pink", "Lime", "Brown"
    };

    private static Map<Integer, ItemProp> items = new HashMap<>();

    static {
        loadData();
    }

    private static void loadData() {
        // Load Blocks
        try (BufferedReader br = new BufferedReader(new FileReader("blocks.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                int id = Integer.parseInt(parts[0]);
                String name = parts[1];
                int toughness = Integer.parseInt(parts[2]);
                String textureName = parts[3];
                int value = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
                items.put(id, new ItemProp(name, id, toughness, textureName, "BLOCK", 0, value));
            }
        } catch (IOException e) { e.printStackTrace(); }

        // Load Items (Tools)
        try (BufferedReader br = new BufferedReader(new FileReader("items.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                int id = Integer.parseInt(parts[0]);
                String name = parts[1];
                String type = parts[2];
                int tier = Integer.parseInt(parts[3]);
                String textureName = parts[4];
                items.put(id, new ItemProp(name, id, 0, textureName, type, tier, 0));
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static ItemProp getItem(int id) {
        return items.getOrDefault(id, new ItemProp("Air", 0, 0, null, "AIR", 0, 0));
    }

    public static java.util.List<Recipe> recipes = new ArrayList<>();

    static {
        // Planks (Shapeless)
        recipes.add(new Recipe(new int[]{WOOD}, PLANKS, 4, true, true));
        // Sticks (Shapeless)
        recipes.add(new Recipe(new int[]{PLANKS, PLANKS}, 200, 4, true, true));
        // Crafter
        recipes.add(new Recipe(new int[]{PLANKS, PLANKS, PLANKS, PLANKS}, CRAFTER, 1, true, false));

        // 3x3 Only recipes (Tools)
        int stick = 200;
        int[] mats = {PLANKS, STONE, 201, 202, 203}; // Wood, Stone, Iron, Gold, Diamond
        int[] swords = {100, 101, 102, 103, 104};
        int[] shovels = {130, 131, 132, 133, 134};
        int[] pickaxes = {110, 111, 112, 113, 114};
        int[] axes = {120, 121, 122, 123, 124};

        for (int i = 0; i < 5; i++) {
            int m = mats[i];
            // Sword
            recipes.add(new Recipe(new int[]{0,m,0, 0,m,0, 0,stick,0}, swords[i], 1, false));
            // Shovel
            recipes.add(new Recipe(new int[]{0,m,0, 0,stick,0, 0,stick,0}, shovels[i], 1, false));
            // Pickaxe
            recipes.add(new Recipe(new int[]{m,m,m, 0,stick,0, 0,stick,0}, pickaxes[i], 1, false));
            // Axe
            recipes.add(new Recipe(new int[]{m,m,0, m,stick,0, 0,stick,0}, axes[i], 1, false));
        }
    }

    public static class Recipe {
        public int[] pattern; // 4 for 2x2, 9 for 3x3, or just ingredient list for shapeless
        public int resultID;
        public int resultCount;
        public boolean is2x2;
        public boolean isShapeless;

        public Recipe(int[] p, int rID, int rC, boolean is2x2, boolean isShapeless) {
            this.pattern = p; this.resultID = rID; this.resultCount = rC; this.is2x2 = is2x2; this.isShapeless = isShapeless;
        }

        public Recipe(int[] p, int rID, int rC, boolean is2x2) {
            this(p, rID, rC, is2x2, false);
        }
    }

    // Keep for backward compatibility if needed, but redirects to getItem
    public static ItemProp getBlock(int id) {
        return getItem(id);
    }

    public static class ItemProp {
        public String name;
        public int id;
        public int toughness; // For blocks
        public String textureName;
        public String type;    // BLOCK, SWORD, PICKAXE, AXE, SHOVEL
        public int tier;
        public int value;
        public BufferedImage texture;

        public ItemProp(String n, int id, int t, String texName, String type, int tier, int value) {
            this.name = n; this.id = id; this.toughness = t; this.textureName = texName;
            this.type = type; this.tier = tier; this.value = value;
            try {
                File imgFile = new File("resources/textures/" + texName);
                if (imgFile.exists()) {
                    this.texture = ImageIO.read(imgFile);
                }
            } catch (Exception e) {}
        }

        public boolean isBlock() { return "BLOCK".equals(type); }

        public float getMiningSpeed(int blockID) {
            ItemProp block = SharedData.getItem(blockID);
            float speed = 2.0f;
            boolean isStoneLike = (blockID == STONE || blockID == IRON_ORE || blockID == GOLD_ORE || blockID == DIAMOND_ORE || blockID == FURNACE);
            boolean isWoodLike = (blockID == WOOD || blockID == PLANKS || blockID == CRAFTER);

            if ("PICKAXE".equals(this.type) && isStoneLike) speed *= (1 + tier * 2);
            if ("SHOVEL".equals(this.type) && blockID == DIRT) speed *= (1 + tier * 2);
            if ("AXE".equals(this.type) && isWoodLike) speed *= (1 + tier * 2);
            return speed;
        }

        public int getDamage() {
            if ("SWORD".equals(type)) return 10 + tier * 5;
            return 2;
        }
    }
}