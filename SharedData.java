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

    private static Map<Integer, BlockProp> blocks = new HashMap<>();

    static {
        loadBlocks();
    }

    private static void loadBlocks() {
        try (BufferedReader br = new BufferedReader(new FileReader("blocks.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                int id = Integer.parseInt(parts[0]);
                String name = parts[1];
                int toughness = Integer.parseInt(parts[2]);
                String textureName = parts[3];

                BufferedImage img = null;
                try {
                    File imgFile = new File("resources/textures/" + textureName);
                    if (imgFile.exists()) {
                        img = ImageIO.read(imgFile);
                    }
                } catch (Exception e) {
                    System.err.println("Could not load texture: " + textureName);
                }

                blocks.put(id, new BlockProp(name, toughness, img));
            }
        } catch (IOException e) {
            System.err.println("Could not load blocks.txt, using defaults.");
            // Fallback defaults
            blocks.put(AIR, new BlockProp("Air", 0, null));
            blocks.put(BEDROCK, new BlockProp("Bedrock", -1, null));
            blocks.put(DIRT, new BlockProp("Dirt", 20, null));
            blocks.put(STONE, new BlockProp("Stone", 60, null));
            blocks.put(WOOD, new BlockProp("Wood", 40, null));
        }
    }

    public static BlockProp getBlock(int id) {
        return blocks.getOrDefault(id, blocks.get(AIR));
    }

    public static class BlockProp {
        public String name;
        public int toughness;
        public BufferedImage texture;
        
        public BlockProp(String n, int t, BufferedImage tex) {
            this.name = n; this.toughness = t; this.texture = tex;
        }
    }
}