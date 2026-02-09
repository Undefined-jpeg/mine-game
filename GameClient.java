import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer; // Explicit import to avoid conflicts

public class GameClient extends JPanel implements ActionListener, KeyListener, MouseListener, MouseWheelListener, MouseMotionListener {
    // --- Network ---
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    
    // --- Game World ---
    private int[][] map = new int[SharedData.MAP_SIZE][SharedData.MAP_SIZE];
    private Map<Integer, PlayerData> otherPlayers = new HashMap<>();

    private static class PlayerData {
        Point pos;
        int money;
        PlayerData(Point p, int m) { this.pos = p; this.money = m; }
    }

    // --- Player State ---
    private int playerX = SharedData.MAP_SIZE / 2 * SharedData.TILE_SIZE;
    private int playerY = SharedData.MAP_SIZE / 2 * SharedData.TILE_SIZE;
    private int camX, camY;
    private static final int PLAYER_SIZE = 20; 

    // --- Inventory ---
    private Inventory inventory = new Inventory(42); // 32 inv + 9 crafting + 1 output
    private int selectedSlot = 0; 
    private boolean isInventoryOpen = false;
    private boolean isCraftingOpen = false;
    private int draggingSlot = -1;
    
    // --- Inputs & Logic ---
    private boolean isLeftMousePressed = false;
    private boolean isRightMousePressed = false;
    private float miningProgress = 0;
    private boolean up, down, left, right;
    private int health = 100;
    private int money = 0;
    private boolean isFullscreen = false;
    private JFrame frame;

    // --- Dash ---
    private boolean isDashing = false;
    private long dashStartTime;
    private double dashVX, dashVY;
    private long lastDashTime = 0;
    private static final long DASH_COOLDOWN = 2000;
    private static final long DASH_DURATION = 200;
    private static final double DASH_SPEED = 15.0;
    private Point dashBurstPoint = null;
    private long dashBurstStartTime = 0;

    // --- Assets ---
    private BufferedImage playerTexture;
    private BufferedImage heartTexture;
    
    // Debugging
    private Point currentMouseWorldPos = new Point(0,0);
    private boolean canReach = false;

    public GameClient(JFrame frame) {
        this.frame = frame;
        this.setPreferredSize(new Dimension(800, 600));
        this.setBackground(new Color(135, 206, 235)); // Sky blue background
        this.setFocusable(true);
        this.addKeyListener(this);
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        this.addMouseWheelListener(this);

        generateWorld(SharedData.MAP_SEED); 
        loadAssets();
        loadMoney();
        loadInventory();
        connectToServer();

        // Give some starting items
        inventory.setItem(0, SharedData.DIRT);
        inventory.setItem(1, SharedData.STONE);
        inventory.setItem(2, SharedData.WOOD);
        inventory.setItem(3, SharedData.CRAFTER);
        inventory.setItem(4, 110); // Wood Pickaxe
        inventory.setItem(5, 100); // Wood Sword

        Timer timer = new Timer(1000 / 60, this); 
        timer.start();
    }

    private void connectToServer() {
        String ip = JOptionPane.showInputDialog(null, "Enter server IP:", "localhost");
        if (ip == null || ip.isEmpty()) ip = "localhost";

        try {
            socket = new Socket(ip, SharedData.PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) processServerMessage(line);
                } catch (IOException e) { e.printStackTrace(); }
            }).start();

        } catch (IOException e) {
            System.out.println("Playing in Singleplayer (No Server Found)");
        }
    }

    private void loadAssets() {
        try {
            playerTexture = ImageIO.read(new File("resources/textures/player.png"));
            heartTexture = ImageIO.read(new File("resources/textures/heart.png"));
        } catch (Exception e) {}
    }

    private void processServerMessage(String line) {
        try {
            String[] parts = line.split(" ");
            String cmd = parts[0];

            if (cmd.equals("PLAYER")) {
                int pid = Integer.parseInt(parts[1]);
                int x = Integer.parseInt(parts[2]);
                int y = Integer.parseInt(parts[3]);
                PlayerData pd = otherPlayers.get(pid);
                if (pd == null) otherPlayers.put(pid, new PlayerData(new Point(x, y), 0));
                else pd.pos = new Point(x, y);
            } 
            else if (cmd.equals("MONEY")) {
                int pid = Integer.parseInt(parts[1]);
                int m = Integer.parseInt(parts[2]);
                PlayerData pd = otherPlayers.get(pid);
                if (pd != null) pd.money = m;
            }
            else if (cmd.equals("BLOCK")) {
                int bx = Integer.parseInt(parts[1]);
                int by = Integer.parseInt(parts[2]);
                int type = Integer.parseInt(parts[3]);
                map[bx][by] = type;
                repaint();
            }
            else if (cmd.equals("DAMAGE")) {
                if (isDashing) return; // Invincible while dashing
                health -= Integer.parseInt(parts[1]);
                if (health <= 0) {
                    health = 100;
                    playerX = SharedData.MAP_SIZE / 2 * SharedData.TILE_SIZE;
                    playerY = SharedData.MAP_SIZE / 2 * SharedData.TILE_SIZE;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void generateWorld(long seed) {
        Random rng = new Random(seed);
        for (int x = 0; x < SharedData.MAP_SIZE; x++) {
            for (int y = 0; y < SharedData.MAP_SIZE; y++) {
                if (x == 0 || x == SharedData.MAP_SIZE - 1 || y == 0 || y == SharedData.MAP_SIZE - 1) {
                    map[x][y] = SharedData.BEDROCK;
                } else {
                    int n = rng.nextInt(1000);
                    if (n < 5) map[x][y] = SharedData.DIAMOND_ORE;
                    else if (n < 15) map[x][y] = SharedData.GOLD_ORE;
                    else if (n < 40) map[x][y] = SharedData.IRON_ORE;
                    else if (n < 100) map[x][y] = SharedData.STONE;
                    else if (n < 130) map[x][y] = SharedData.WOOD;
                    else if (n < 250) map[x][y] = SharedData.DIRT;
                    else map[x][y] = SharedData.AIR;
                }
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // 1. Calculate Mouse Position
        updateMousePosition();

        // 2. Dash Logic
        if (isDashing) {
            long elapsed = System.currentTimeMillis() - dashStartTime;
            if (elapsed > DASH_DURATION) {
                isDashing = false;
            } else {
                int nextX = (int)(playerX + dashVX);
                int nextY = (int)(playerY + dashVY);
                if (canMove(nextX, nextY)) {
                    playerX = nextX;
                    playerY = nextY;
                } else {
                    isDashing = false;
                }
            }
        }

        // 3. Player Movement
        if (!isInventoryOpen && !isDashing) {
            int speed = 5;
            if (up && canMove(playerX, playerY - speed)) playerY -= speed;
            if (down && canMove(playerX, playerY + speed)) playerY += speed;
            if (left && canMove(playerX - speed, playerY)) playerX -= speed;
            if (right && canMove(playerX + speed, playerY)) playerX += speed;
        }

        camX = playerX - getWidth() / 2;
        camY = playerY - getHeight() / 2;

        if (out != null) {
            out.println("POS " + playerX + " " + playerY);
            out.println("MONEY " + money);
        }

        // 4. Mining Logic
        if (!isInventoryOpen && !isCraftingOpen) handleMiningAndPlacing();
        
        repaint();
    }

    private void updateMousePosition() {
        try {
            Point p = MouseInfo.getPointerInfo().getLocation();
            SwingUtilities.convertPointFromScreen(p, this);
            
            // Convert to World Coordinates
            int worldX = p.x + camX;
            int worldY = p.y + camY;
            currentMouseWorldPos = new Point(worldX, worldY);

            // Check Reach Distance (Center of player to Mouse)
            double dist = Math.hypot((playerX + 10) - worldX, (playerY + 10) - worldY);
            canReach = dist < 200; // 200 Pixel reach

        } catch (Exception e) {
            // Mouse might be off screen
        }
    }

    private void handleMiningAndPlacing() {
        if (!canReach) {
            miningProgress = 0;
            return;
        }

        int gridX = currentMouseWorldPos.x / SharedData.TILE_SIZE;
        int gridY = currentMouseWorldPos.y / SharedData.TILE_SIZE;

        if (gridX < 0 || gridX >= SharedData.MAP_SIZE || gridY < 0 || gridY >= SharedData.MAP_SIZE) {
            miningProgress = 0;
            return;
        }

        int blockID = map[gridX][gridY];
        int heldItemID = inventory.getItem(selectedSlot);
        SharedData.ItemProp heldItem = SharedData.getItem(heldItemID);

        // --- BREAKING (Left Click) ---
        if (isLeftMousePressed) {
            if (blockID != SharedData.AIR) {
                SharedData.ItemProp prop = SharedData.getBlock(blockID);
                miningProgress += heldItem.getMiningSpeed(blockID);
                if (miningProgress >= prop.toughness && prop.toughness != -1) {
                    map[gridX][gridY] = SharedData.AIR;
                    money += prop.value;

                    int dropID = blockID;
                    if (blockID == SharedData.IRON_ORE) dropID = 201; // Iron Ingot
                    else if (blockID == SharedData.GOLD_ORE) dropID = 202; // Gold Ingot
                    else if (blockID == SharedData.DIAMOND_ORE) dropID = 203; // Diamond

                    addToInventory(dropID);
                    saveMoney();
                    saveInventory();
                    if (out != null) out.println("BLOCK " + gridX + " " + gridY + " " + SharedData.AIR);
                    miningProgress = 0;
                }
            } else {
                miningProgress = 0;
                // Sword attack check?
                if (heldItem.type.equals("SWORD")) {
                    for (Map.Entry<Integer, PlayerData> entry : otherPlayers.entrySet()) {
                        Point p = entry.getValue().pos;
                        if (Math.hypot(p.x - currentMouseWorldPos.x, p.y - currentMouseWorldPos.y) < 20) {
                            if (out != null) out.println("HIT " + entry.getKey() + " " + heldItem.getDamage());
                            isLeftMousePressed = false; // Prevent rapid hit
                        }
                    }
                }
            }
        } else {
            miningProgress = 0;
        }

        // --- PLACING / INTERACTION (Right Click) ---
        if (isRightMousePressed) {
            if (blockID == SharedData.CRAFTER) {
                isCraftingOpen = true;
                isRightMousePressed = false;
                return;
            }
            if (blockID == SharedData.AIR && heldItem.isBlock() && heldItemID != 0) {
                Rectangle pRect = new Rectangle(playerX, playerY, PLAYER_SIZE, PLAYER_SIZE);
                Rectangle bRect = new Rectangle(gridX * 32, gridY * 32, 32, 32);

                if (!pRect.intersects(bRect)) {
                    map[gridX][gridY] = heldItemID;
                    inventory.setItem(selectedSlot, 0); // Consume item!
                    saveInventory();
                    if (out != null) out.println("BLOCK " + gridX + " " + gridY + " " + heldItemID);
                    isRightMousePressed = false;
                }
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int tilesX = (getWidth() / 32) + 2;
        int tilesY = (getHeight() / 32) + 2;

        int startX = Math.max(0, camX / 32);
        int startY = Math.max(0, camY / 32);
        int endX = Math.min(SharedData.MAP_SIZE, startX + tilesX);
        int endY = Math.min(SharedData.MAP_SIZE, startY + tilesY);

        // 1. Draw Map
        for (int x = startX; x < endX; x++) {
            for (int y = startY; y < endY; y++) {
                int id = map[x][y];
                int sx = x * 32 - camX;
                int sy = y * 32 - camY;

                SharedData.ItemProp prop = SharedData.getBlock(id);
                if (prop.texture != null) {
                    g.drawImage(prop.texture, sx, sy, 32, 32, null);
                } else {
                    g.setColor(id == SharedData.AIR ? new Color(60, 160, 60) : Color.MAGENTA);
                    g.fillRect(sx, sy, 32, 32);
                }

                // Optional grid lines
                g.setColor(new Color(0, 0, 0, 20));
                g.drawRect(sx, sy, 32, 32);
            }
        }

        // 2. Draw Other Players
    for (Map.Entry<Integer, PlayerData> entry : otherPlayers.entrySet()) {
        PlayerData pd = entry.getValue();
        g.setColor(Color.BLUE);
        g.fillRect(pd.pos.x - camX, pd.pos.y - camY, PLAYER_SIZE, PLAYER_SIZE);
        g.setColor(Color.YELLOW);
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.drawString("$" + pd.money, pd.pos.x - camX, pd.pos.y - camY - 5);
    }

        // 3. Draw Local Player
        Graphics2D g2d = (Graphics2D) g;
        int px = playerX - camX;
        int py = playerY - camY;

        // Rotate player/item towards mouse
        double angle = Math.atan2((currentMouseWorldPos.y - camY) - (py + 10), (currentMouseWorldPos.x - camX) - (px + 10));

        g2d.translate(px + 10, py + 10);

        // Draw player
        if (playerTexture != null) {
            g2d.drawImage(playerTexture, -10, -10, 20, 20, null);
        } else {
            g2d.setColor(Color.RED);
            g2d.fillRect(-10, -10, 20, 20);
        }

        // Draw held item
        int heldItemID = inventory.getItem(selectedSlot);
        SharedData.ItemProp heldItem = SharedData.getItem(heldItemID);
        if (heldItemID != SharedData.AIR && heldItem.texture != null) {
            g2d.rotate(angle);
            g2d.drawImage(heldItem.texture, 10, -8, 16, 16, null);
            g2d.rotate(-angle);
        }

        g2d.translate(-(px + 10), -(py + 10));

        // Draw Dash Burst
        if (dashBurstPoint != null) {
            long elapsed = System.currentTimeMillis() - dashBurstStartTime;
            if (elapsed < 300) {
                g2d.setColor(new Color(255, 255, 255, (int)(255 * (1 - elapsed / 300.0))));
                int size = (int)(20 + elapsed / 5.0);
                g2d.drawOval(dashBurstPoint.x - camX - size / 2, dashBurstPoint.y - camY - size / 2, size, size);
            } else {
                dashBurstPoint = null;
            }
        }

        // --- NEW: LASER SIGHT (DEBUG) ---
        // Draws a line from player to mouse. 
        // RED = Out of range, GREEN = In range.
        if (canReach) g.setColor(Color.GREEN);
        else g.setColor(Color.RED);
        
        g.drawLine(px + 10, py + 10, currentMouseWorldPos.x - camX, currentMouseWorldPos.y - camY);

        // Draw Selection Box
        int gx = currentMouseWorldPos.x / 32;
        int gy = currentMouseWorldPos.y / 32;
        g.drawRect(gx * 32 - camX, gy * 32 - camY, 32, 32);

        // 4. HUD
        drawHUD(g);
    }

    private void drawHUD(Graphics g) {
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.drawString("Money: $" + money, 20, 30);

        int hotbarSlots = 8;
        int barWidth = hotbarSlots * 60;
        int startX = (getWidth() - barWidth) / 2;
        int startY = getHeight() - 80;

        // --- Health Bar ---
        for (int i = 0; i < 10; i++) {
            int hx = startX + (i * 20);
            int hy = startY - 30;
            if (i < health / 10) {
                if (heartTexture != null) g.drawImage(heartTexture, hx, hy, 16, 16, null);
                else { g.setColor(Color.RED); g.fillRect(hx, hy, 16, 16); }
            } else {
                g.setColor(Color.BLACK); g.drawRect(hx, hy, 16, 16);
            }
        }

        // Draw Hotbar Background
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(startX - 5, startY - 5, barWidth + 5, 60, 10, 10);

        for (int i = 0; i < hotbarSlots; i++) {
            int x = startX + (i * 60);

            // Slot Background
            if (i == selectedSlot) {
                g.setColor(new Color(255, 255, 255, 200));
                g.fillRoundRect(x, startY, 50, 50, 5, 5);
                g.setColor(Color.YELLOW);
                g.drawRoundRect(x, startY, 50, 50, 5, 5);
            } else {
                g.setColor(new Color(255, 255, 255, 50));
                g.fillRoundRect(x, startY, 50, 50, 5, 5);
                g.setColor(Color.WHITE);
                g.drawRoundRect(x, startY, 50, 50, 5, 5);
            }

            int itemId = inventory.getItem(i);
            SharedData.ItemProp prop = SharedData.getItem(itemId);
            if (prop.texture != null) {
                g.drawImage(prop.texture, x + 5, startY + 5, 40, 40, null);
            }

            g.setColor(Color.WHITE);
            g.drawString(String.valueOf(i + 1), x + 5, startY + 15);
        }

        if (isInventoryOpen) drawInventory(g);
        if (isCraftingOpen) drawCrafting(g);

        // --- Mining Progress Bar ---
        if (miningProgress > 0) {
            int gridX = currentMouseWorldPos.x / SharedData.TILE_SIZE;
            int gridY = currentMouseWorldPos.y / SharedData.TILE_SIZE;
            if (gridX >= 0 && gridX < SharedData.MAP_SIZE && gridY >= 0 && gridY < SharedData.MAP_SIZE) {
                int blockID = map[gridX][gridY];
                if (blockID != SharedData.AIR) {
                    SharedData.ItemProp prop = SharedData.getItem(blockID);
                    if (prop.toughness > 0) {
                        float percent = Math.min(1.0f, miningProgress / prop.toughness);
                        int pWidth = 200;
                        int px = (getWidth() - pWidth) / 2;
                        int py = startY + 65;

                        // Background
                        g.setColor(Color.BLACK);
                        g.fillRect(px, py, pWidth, 15);
                        // Progress
                        g.setColor(Color.GREEN);
                        g.fillRect(px + 2, py + 2, (int)((pWidth - 4) * percent), 11);
                        // Border
                        g.setColor(Color.WHITE);
                        g.drawRect(px, py, pWidth, 15);
                    }
                }
            }
        }
    }

    private void drawInventory(Graphics g) {
        drawSlots(g, 0, 32, 4, 8, (getWidth() - 8 * 55) / 2, (getHeight() - 4 * 55) / 2);
        drawDraggingItem(g);
    }

    private void drawCrafting(Graphics g) {
        int invX = (getWidth() - 8 * 55) / 2;
        int invY = (getHeight() - 4 * 55) / 2 + 100;

        // Draw Main Inventory below crafting
        drawSlots(g, 0, 32, 4, 8, invX, invY);

        // Draw Crafting Grid (3x3)
        int gridX = (getWidth() - 3 * 55) / 2 - 100;
        int gridY = (getHeight() - 4 * 55) / 2 - 50;
        g.setColor(Color.WHITE);
        g.drawString("Crafting", gridX, gridY - 10);
        drawSlots(g, 32, 9, 3, 3, gridX, gridY);

        // Draw Arrow
        g.drawString("->", gridX + 3 * 55 + 20, gridY + 1 * 55 + 25);

        // Draw Output
        drawSlots(g, 41, 1, 1, 1, gridX + 3 * 55 + 60, gridY + 1 * 55);

        drawDraggingItem(g);
    }

    private void drawSlots(Graphics g, int startIdx, int count, int rows, int cols, int xOff, int yOff) {
        int slotSize = 50, gap = 5;
        int invWidth = cols * (slotSize + gap);
        int invHeight = rows * (slotSize + gap);

        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(xOff - 10, yOff - 10, invWidth + 20, invHeight + 20);

        for (int i = 0; i < count; i++) {
            int r = i / cols;
            int c = i % cols;
            int x = xOff + c * (slotSize + gap);
            int y = yOff + r * (slotSize + gap);

            g.setColor(Color.GRAY);
            g.fillRect(x, y, slotSize, slotSize);
            g.setColor(Color.WHITE);
            g.drawRect(x, y, slotSize, slotSize);

            int itemId = inventory.getItem(startIdx + i);
            SharedData.ItemProp prop = SharedData.getItem(itemId);
            if (prop.texture != null) {
                g.drawImage(prop.texture, x + 5, y + 5, 40, 40, null);
            }
        }
    }

    private void drawDraggingItem(Graphics g) {
        if (draggingSlot != -1) {
            int itemId = inventory.getItem(draggingSlot);
            SharedData.ItemProp prop = SharedData.getItem(itemId);
            if (prop.texture != null) {
                Point p = getMousePosition();
                if (p != null) g.drawImage(prop.texture, p.x - 20, p.y - 20, 40, 40, null);
            }
        }
    }

    // --- Helpers ---
    private boolean canMove(int nextX, int nextY) {
        return !isSolid(nextX, nextY) &&                         
               !isSolid(nextX + PLAYER_SIZE - 1, nextY) &&       
               !isSolid(nextX, nextY + PLAYER_SIZE - 1) &&       
               !isSolid(nextX + PLAYER_SIZE - 1, nextY + PLAYER_SIZE - 1); 
    }

    private boolean isSolid(int pixelX, int pixelY) {
        int gridX = pixelX / SharedData.TILE_SIZE;
        int gridY = pixelY / SharedData.TILE_SIZE;
        if (gridX < 0 || gridX >= SharedData.MAP_SIZE || gridY < 0 || gridY >= SharedData.MAP_SIZE) return true;
        return map[gridX][gridY] != SharedData.AIR;
    }

    // --- Inputs ---
    public void mousePressed(MouseEvent e) {
        if (isInventoryOpen) {
            draggingSlot = getSlotAt(e.getX(), e.getY(), 0, 32, 4, 8, (getWidth() - 8 * 55) / 2, (getHeight() - 4 * 55) / 2);
            return;
        }
        if (isCraftingOpen) {
            int invX = (getWidth() - 8 * 55) / 2;
            int invY = (getHeight() - 4 * 55) / 2 + 100;
            draggingSlot = getSlotAt(e.getX(), e.getY(), 0, 32, 4, 8, invX, invY);
            if (draggingSlot != -1) return;

            int gridX = (getWidth() - 3 * 55) / 2 - 100;
            int gridY = (getHeight() - 4 * 55) / 2 - 50;
            draggingSlot = getSlotAt(e.getX(), e.getY(), 32, 9, 3, 3, gridX, gridY);
            if (draggingSlot != -1) return;

            draggingSlot = getSlotAt(e.getX(), e.getY(), 41, 1, 1, 1, gridX + 3 * 55 + 60, gridY + 1 * 55);
            return;
        }
        if (e.getButton() == MouseEvent.BUTTON1) isLeftMousePressed = true;
        if (e.getButton() == MouseEvent.BUTTON3) isRightMousePressed = true;
    }
    public void mouseReleased(MouseEvent e) {
        if ((isInventoryOpen || isCraftingOpen) && draggingSlot != -1) {
            int targetSlot = -1;
            if (isInventoryOpen) {
                targetSlot = getSlotAt(e.getX(), e.getY(), 0, 32, 4, 8, (getWidth() - 8 * 55) / 2, (getHeight() - 4 * 55) / 2);
            } else if (isCraftingOpen) {
                int invX = (getWidth() - 8 * 55) / 2;
                int invY = (getHeight() - 4 * 55) / 2 + 100;
                targetSlot = getSlotAt(e.getX(), e.getY(), 0, 32, 4, 8, invX, invY);
                if (targetSlot == -1) {
                    int gridX = (getWidth() - 3 * 55) / 2 - 100;
                    int gridY = (getHeight() - 4 * 55) / 2 - 50;
                    targetSlot = getSlotAt(e.getX(), e.getY(), 32, 9, 3, 3, gridX, gridY);
                }
            }

            if (targetSlot != -1 && targetSlot != 41) { // Cannot drop into output slot
                if (draggingSlot == 41) {
                    // Take from output: consume ingredients
                    if (inventory.getItem(targetSlot) == 0) {
                        inventory.setItem(targetSlot, inventory.getItem(41));
                        consumeIngredients();
                    }
                } else {
                    inventory.swap(draggingSlot, targetSlot);
                }
                if (isCraftingOpen) updateCrafting();
                saveInventory();
            }
            draggingSlot = -1;
            repaint();
            return;
        }
        if (e.getButton() == MouseEvent.BUTTON1) isLeftMousePressed = false;
        if (e.getButton() == MouseEvent.BUTTON3) isRightMousePressed = false;
    }
    public void mouseClicked(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
    public void mouseDragged(MouseEvent e) { updateMousePosition(); }
    public void mouseMoved(MouseEvent e) { updateMousePosition(); }
    
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_W) up = true;
        if (k == KeyEvent.VK_S) down = true;
        if (k == KeyEvent.VK_A) left = true;
        if (k == KeyEvent.VK_D) right = true;
        if (k >= KeyEvent.VK_1 && k <= KeyEvent.VK_8) {
            selectedSlot = k - KeyEvent.VK_1;
            repaint();
        }
        if (k == KeyEvent.VK_E) {
            if (isCraftingOpen) {
                isCraftingOpen = false;
            } else {
                isInventoryOpen = !isInventoryOpen;
            }
            isLeftMousePressed = false;
            isRightMousePressed = false;
            repaint();
        }
        if (k == KeyEvent.VK_ESCAPE) {
            isInventoryOpen = false;
            isCraftingOpen = false;
            repaint();
        }
        if (k == KeyEvent.VK_F11) {
            toggleFullscreen();
        }
        if (k == KeyEvent.VK_Q) {
            long now = System.currentTimeMillis();
            if (now - lastDashTime > DASH_COOLDOWN && !isInventoryOpen) {
                isDashing = true;
                dashStartTime = now;
                lastDashTime = now;

                double angle = Math.atan2(currentMouseWorldPos.y - (playerY + 10), currentMouseWorldPos.x - (playerX + 10));
                dashVX = Math.cos(angle) * DASH_SPEED;
                dashVY = Math.sin(angle) * DASH_SPEED;

                dashBurstPoint = new Point(playerX + 10, playerY + 10);
                dashBurstStartTime = now;
            }
        }
    }

    private void addToInventory(int id) {
        if (id == 0) return;
        for (int i = 0; i < 32; i++) {
            if (inventory.getItem(i) == 0) {
                inventory.setItem(i, id);
                return;
            }
        }
    }

    private void saveMoney() {
        try (PrintWriter pw = new PrintWriter(new FileWriter("money.txt"))) {
            pw.println(money);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void saveInventory() {
        try (PrintWriter pw = new PrintWriter(new FileWriter("inventory.txt"))) {
            for (int i = 0; i < 32; i++) {
                pw.println(inventory.getItem(i));
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadMoney() {
        File f = new File("money.txt");
        if (f.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                money = Integer.parseInt(br.readLine());
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void loadInventory() {
        File f = new File("inventory.txt");
        if (f.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                for (int i = 0; i < 32; i++) {
                    String line = br.readLine();
                    if (line != null) inventory.setItem(i, Integer.parseInt(line));
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private int getSlotAt(int mx, int my, int startIdx, int count, int rows, int cols, int startX, int startY) {
        int slotSize = 50, gap = 5;
        int c = (mx - startX) / (slotSize + gap);
        int r = (my - startY) / (slotSize + gap);
        if (c >= 0 && c < cols && r >= 0 && r < rows) {
            int idx = r * cols + c;
            if (idx < count) return startIdx + idx;
        }
        return -1;
    }

    private void updateCrafting() {
        int[] g = new int[9];
        for (int i = 0; i < 9; i++) g[i] = inventory.getItem(32 + i);

        inventory.setItem(41, 0); // Default empty

        // 1. Planks (1 Log anywhere)
        int logs = 0;
        for(int i=0; i<9; i++) if(g[i] == SharedData.WOOD) logs++;
        if(logs == 1 && countItems(g) == 1) { inventory.setItem(41, SharedData.PLANKS); return; }

        // 2. Sticks (2 Planks vertical)
        for(int i=0; i<6; i++) {
            if(g[i] == SharedData.PLANKS && g[i+3] == SharedData.PLANKS && countItems(g) == 2) {
                inventory.setItem(41, 200); return;
            }
        }

        // 3. Tools
        checkToolRecipe(g, "SWORD", new int[]{100, 101, 102, 103, 104});
        checkToolRecipe(g, "SHOVEL", new int[]{130, 131, 132, 133, 134});
        checkToolRecipe(g, "PICKAXE", new int[]{110, 111, 112, 113, 114});
        checkToolRecipe(g, "AXE", new int[]{120, 121, 122, 123, 124});
    }

    private int countItems(int[] g) {
        int c = 0;
        for(int i : g) if(i != 0) c++;
        return c;
    }

    private void checkToolRecipe(int[] g, String type, int[] outputIDs) {
        int stick = 200;
        int[] mats = {SharedData.PLANKS, SharedData.STONE, 201, 202, 203}; // Wood, Stone, Iron, Gold, Diamond

        for (int i = 0; i < 5; i++) {
            int m = mats[i];
            boolean match = false;
            if(type.equals("SWORD")) match = (g[1]==m && g[4]==m && g[7]==stick && countItems(g)==3);
            else if(type.equals("SHOVEL")) match = (g[1]==m && g[4]==stick && g[7]==stick && countItems(g)==3);
            else if(type.equals("PICKAXE")) match = (g[0]==m && g[1]==m && g[2]==m && g[4]==stick && g[7]==stick && countItems(g)==5);
            else if(type.equals("AXE")) match = (g[0]==m && g[1]==m && g[3]==m && g[4]==stick && g[7]==stick && countItems(g)==5);

            if(match) { inventory.setItem(41, outputIDs[i]); return; }
        }
    }

    private void consumeIngredients() {
        for(int i=32; i<=40; i++) inventory.setItem(i, 0);
        updateCrafting();
    }

    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        frame.dispose();
        if (isFullscreen) {
            frame.setUndecorated(true);
            if (gd.isFullScreenSupported()) {
                gd.setFullScreenWindow(frame);
            } else {
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            }
        } else {
            if (gd.getFullScreenWindow() == frame) {
                gd.setFullScreenWindow(null);
            }
            frame.setUndecorated(false);
            frame.setExtendedState(JFrame.NORMAL);
            frame.setSize(800, 600);
        }
        frame.setVisible(true);
    }
    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_W) up = false;
        if (k == KeyEvent.VK_S) down = false;
        if (k == KeyEvent.VK_A) left = false;
        if (k == KeyEvent.VK_D) right = false;
    }
    public void mouseWheelMoved(MouseWheelEvent e) {
        selectedSlot -= e.getWheelRotation();
        if(selectedSlot < 0) selectedSlot = 7;
        if(selectedSlot > 7) selectedSlot = 0;
        repaint();
    }
    public void keyTyped(KeyEvent e) {}

    public static void main(String[] args) {
        JFrame frame = new JFrame("Java Multiplayer Voxel");
        GameClient client = new GameClient(frame);
        frame.setContentPane(client);
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (gd.isFullScreenSupported()) {
            gd.setFullScreenWindow(frame);
            client.isFullscreen = true;
        } else {
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        }

        frame.setVisible(true);
    }
}