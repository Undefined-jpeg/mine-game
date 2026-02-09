import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;

public class SharedData {
    // --- Configuration ---
    public static final int MAP_SIZE = 1024;
    public static final int TILE_SIZE = 32;
    public static final int PORT = 9999;
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
            boolean isStoneLike = (blockID == STONE || blockID == IRON_ORE || blockID == GOLD_ORE || blockID == DIAMOND_ORE);
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