import java.awt.Color;

public class SharedData {
    // --- Configuration ---
    public static final int MAP_SIZE = 1024;
    public static final int TILE_SIZE = 32;
    public static final int PORT = 9999;
    public static final long MAP_SEED = 12345; // Ensures everyone generates the same map initially

    // --- Block IDs ---
    public static final int AIR = 0;
    public static final int BEDROCK = 1;
    public static final int DIRT = 2;
    public static final int STONE = 3;
    public static final int WOOD = 4;

    // --- Block Properties ---
    public static BlockProp getBlock(int id) {
        switch (id) {
            case BEDROCK: return new BlockProp("Bedrock", -1, Color.DARK_GRAY);
            case DIRT:    return new BlockProp("Dirt", 20, new Color(100, 70, 30));
            case STONE:   return new BlockProp("Stone", 60, Color.GRAY);
            case WOOD:    return new BlockProp("Wood", 40, new Color(139, 69, 19));
            default:      return new BlockProp("Air", 0, null);
        }
    }

    public static class BlockProp {
        public String name;
        public int toughness; // -1 means unbreakable
        public Color color;
        
        public BlockProp(String n, int t, Color c) {
            this.name = n; this.toughness = t; this.color = c;
        }
    }
}