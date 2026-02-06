import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.Timer; // Explicit import to avoid conflicts

public class GameClient extends JPanel implements ActionListener, KeyListener, MouseListener, MouseWheelListener, MouseMotionListener {
    // --- Network ---
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    
    // --- Game World ---
    private int[][] map = new int[SharedData.MAP_SIZE][SharedData.MAP_SIZE];
    private Map<Integer, Point> otherPlayers = new HashMap<>();

    // --- Player State ---
    private int playerX = SharedData.MAP_SIZE / 2 * SharedData.TILE_SIZE;
    private int playerY = SharedData.MAP_SIZE / 2 * SharedData.TILE_SIZE;
    private int camX, camY;
    private static final int PLAYER_SIZE = 20; 

    // --- Inventory ---
    private int[] inventory = new int[]{SharedData.DIRT, SharedData.STONE, SharedData.WOOD, SharedData.BEDROCK, SharedData.AIR}; 
    private int selectedSlot = 0; 
    
    // --- Inputs & Logic ---
    private boolean isLeftMousePressed = false;
    private boolean isRightMousePressed = false;
    private float miningProgress = 0;
    private boolean up, down, left, right;
    
    // Debugging
    private Point currentMouseWorldPos = new Point(0,0);
    private boolean canReach = false;

    public GameClient() {
        this.setPreferredSize(new Dimension(800, 600));
        this.setBackground(new Color(135, 206, 235)); // Sky blue background
        this.setFocusable(true);
        this.addKeyListener(this);
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        this.addMouseWheelListener(this);

        generateWorld(SharedData.MAP_SEED); 
        connectToServer();

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

    private void processServerMessage(String line) {
        try {
            String[] parts = line.split(" ");
            String cmd = parts[0];

            if (cmd.equals("PLAYER")) {
                int pid = Integer.parseInt(parts[1]);
                int x = Integer.parseInt(parts[2]);
                int y = Integer.parseInt(parts[3]);
                otherPlayers.put(pid, new Point(x, y));
            } 
            else if (cmd.equals("BLOCK")) {
                int bx = Integer.parseInt(parts[1]);
                int by = Integer.parseInt(parts[2]);
                int type = Integer.parseInt(parts[3]);
                map[bx][by] = type;
                repaint();
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
                    int n = rng.nextInt(100);
                    if (n < 5) map[x][y] = SharedData.STONE;
                    else if (n < 8) map[x][y] = SharedData.WOOD;
                    else if (n < 20) map[x][y] = SharedData.DIRT;
                    else map[x][y] = SharedData.AIR;
                }
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // 1. Calculate Mouse Position (The "Brute Force" way)
        updateMousePosition();

        // 2. Player Movement
        int speed = 5;
        if (up && canMove(playerX, playerY - speed)) playerY -= speed;
        if (down && canMove(playerX, playerY + speed)) playerY += speed;
        if (left && canMove(playerX - speed, playerY)) playerX -= speed;
        if (right && canMove(playerX + speed, playerY)) playerX += speed;

        camX = playerX - 400;
        camY = playerY - 300;

        if (out != null) out.println("POS " + playerX + " " + playerY);

        // 3. Mining Logic
        handleMiningAndPlacing();
        
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

        // --- BREAKING (Left Click) ---
        if (isLeftMousePressed) {
            if (blockID != SharedData.AIR) {
                SharedData.BlockProp prop = SharedData.getBlock(blockID);
                miningProgress += 2.0f;
                if (miningProgress >= prop.toughness && prop.toughness != -1) {
                    map[gridX][gridY] = SharedData.AIR;
                    if (out != null) out.println("BLOCK " + gridX + " " + gridY + " " + SharedData.AIR);
                    miningProgress = 0;
                }
            } else {
                miningProgress = 0;
            }
        } else {
            miningProgress = 0;
        }

        // --- PLACING (Right Click) ---
        if (isRightMousePressed) {
            if (blockID == SharedData.AIR) {
                int item = inventory[selectedSlot];
                if (item != SharedData.AIR) {
                    Rectangle pRect = new Rectangle(playerX, playerY, PLAYER_SIZE, PLAYER_SIZE);
                    Rectangle bRect = new Rectangle(gridX * 32, gridY * 32, 32, 32);

                    if (!pRect.intersects(bRect)) {
                        map[gridX][gridY] = item;
                        if (out != null) out.println("BLOCK " + gridX + " " + gridY + " " + item);
                    }
                }
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int startX = Math.max(0, camX / 32);
        int startY = Math.max(0, camY / 32);
        int endX = Math.min(SharedData.MAP_SIZE, startX + 27);
        int endY = Math.min(SharedData.MAP_SIZE, startY + 21);

        // 1. Draw Map
        for (int x = startX; x < endX; x++) {
            for (int y = startY; y < endY; y++) {
                int id = map[x][y];
                int sx = x * 32 - camX;
                int sy = y * 32 - camY;

                SharedData.BlockProp prop = SharedData.getBlock(id);
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
        g.setColor(Color.BLUE);
        for (Point p : otherPlayers.values()) g.fillRect(p.x - camX, p.y - camY, PLAYER_SIZE, PLAYER_SIZE);

        // 3. Draw Local Player
        int px = playerX - camX;
        int py = playerY - camY;
        g.setColor(Color.RED);
        g.fillRect(px, py, PLAYER_SIZE, PLAYER_SIZE);

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
        int barWidth = 5 * 60;
        int startX = (800 - barWidth) / 2;
        int startY = 520;

        // Draw Hotbar Background
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(startX - 5, startY - 5, barWidth + 5, 60, 10, 10);

        for (int i = 0; i < 5; i++) {
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

            int itemId = inventory[i];
            SharedData.BlockProp prop = SharedData.getBlock(itemId);
            if (prop.texture != null) {
                g.drawImage(prop.texture, x + 5, startY + 5, 40, 40, null);
            }

            g.setColor(Color.WHITE);
            g.drawString(String.valueOf(i + 1), x + 5, startY + 15);
        }

        // --- Mining Progress Bar ---
        if (miningProgress > 0) {
            int gridX = currentMouseWorldPos.x / SharedData.TILE_SIZE;
            int gridY = currentMouseWorldPos.y / SharedData.TILE_SIZE;
            if (gridX >= 0 && gridX < SharedData.MAP_SIZE && gridY >= 0 && gridY < SharedData.MAP_SIZE) {
                int blockID = map[gridX][gridY];
                if (blockID != SharedData.AIR) {
                    SharedData.BlockProp prop = SharedData.getBlock(blockID);
                    if (prop.toughness > 0) {
                        float percent = Math.min(1.0f, miningProgress / prop.toughness);
                        int pWidth = 200;
                        int px = (800 - pWidth) / 2;
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
        if (e.getButton() == MouseEvent.BUTTON1) isLeftMousePressed = true;
        if (e.getButton() == MouseEvent.BUTTON3) isRightMousePressed = true;
    }
    public void mouseReleased(MouseEvent e) {
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
        if (k >= KeyEvent.VK_1 && k <= KeyEvent.VK_5) {
            selectedSlot = k - KeyEvent.VK_1;
            repaint();
        }
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
        if(selectedSlot < 0) selectedSlot = 4;
        if(selectedSlot > 4) selectedSlot = 0;
        repaint();
    }
    public void keyTyped(KeyEvent e) {}

    public static void main(String[] args) {
        JFrame frame = new JFrame("Java Multiplayer Voxel");
        frame.setContentPane(new GameClient());
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}