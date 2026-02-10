import java.io.*;
import java.net.*;
import java.util.*;
import java.net.URL;

public class GameServer {
    private static final Map<Integer, PrintWriter> clients = new HashMap<>();
    private static int nextPlayerId = 1;
    private static int nextHulcsId = 1;

    private static final Map<String, Integer> worldDiff = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, String> containerDiff = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, String> hulcsDiff = new java.util.concurrent.ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        System.out.println("---------------------------------------");
        System.out.println("🌍 MINING GAME SERVER 🌍");
        System.out.println("---------------------------------------");
        System.out.println("Starting Server on port " + SharedData.PORT + "...");

        try {
            System.out.println("🏠 Local IP: " + InetAddress.getLocalHost().getHostAddress());
        } catch (Exception e) {
            System.out.println("❌ Could not determine local IP.");
        }

        new Thread(() -> {
            try {
                URL whatismyip = new URL("https://checkip.amazonaws.com");
                BufferedReader in = new BufferedReader(new InputStreamReader(whatismyip.openStream()));
                String ip = in.readLine();
                System.out.println("🌐 Public IP: " + ip);
                System.out.println("📢 Share this Public IP with your friends!");
                System.out.println("⚠️  Note: You MUST enable Port Forwarding for port " + SharedData.PORT + " on your router.");
            } catch (Exception e) {
                System.out.println("🌐 Public IP: Could not be determined automatically.");
            }
        }).start();

        ServerSocket serverSocket = new ServerSocket(SharedData.PORT);

        while (true) {
            Socket socket = serverSocket.accept();
            System.out.println("New player connected!");
            
            // Create a thread for this player
            ClientHandler handler = new ClientHandler(socket, nextPlayerId++);
            new Thread(handler).start();
        }
    }

    // Handles one specific player
    private static class ClientHandler implements Runnable {
        private Socket socket;
        private int id;
        private PrintWriter out;

        public ClientHandler(Socket socket, int id) {
            this.socket = socket;
            this.id = id;
        }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Add to list of active players
                synchronized (clients) {
                    clients.put(id, out);
                }

                // Tell the player their ID and world state
                out.println("LOGIN " + id + " " + nextHulcsId);
                for (Map.Entry<String, Integer> e : worldDiff.entrySet()) out.println("BLOCK " + e.getKey().replace(",", " ") + " " + e.getValue());
                for (Map.Entry<String, String> e : containerDiff.entrySet()) out.println("CONT " + e.getKey().replace(",", " ") + " " + e.getValue());
                for (Map.Entry<String, String> e : hulcsDiff.entrySet()) out.println("HULCS_DATA " + e.getKey().replace(",", " ") + " " + e.getValue());

                String line;
                while ((line = in.readLine()) != null) {
                    // Protocol: "ACTION data..."
                    String[] parts = line.split(" ");
                    String command = parts[0];

                    if (command.equals("POS")) {
                        // Player moved: "POS x y"
                        // Forward this to everyone else so they see the player
                        broadcast("PLAYER " + id + " " + parts[1] + " " + parts[2], id);
                    } 
                    else if (command.equals("MONEY")) {
                        // Player money update: "MONEY balance"
                        broadcast("MONEY " + id + " " + parts[1], id);
                    }
                    else if (command.equals("COLOR")) {
                        // Player color update: "COLOR r g b"
                        broadcast("COLOR " + id + " " + parts[1] + " " + parts[2] + " " + parts[3], id);
                    }
                    else if (command.equals("DROP")) {
                        // "DROP id x y"
                        broadcast(line, id);
                    }
                    else if (command.equals("CONT")) {
                        // "CONT x y slot id count data"
                        containerDiff.put(parts[1]+","+parts[2]+","+parts[3], parts[4]+" "+parts[5]+" "+parts[6]);
                        broadcast(line, id);
                    }
                    else if (command.equals("BLOCK")) {
                        // Block changed: "BLOCK x y type"
                        worldDiff.put(parts[1]+","+parts[2], Integer.parseInt(parts[3]));
                        broadcast(line, -1);
                    }
                    else if (command.equals("HULCS_DATA")) {
                        // "HULCS_DATA hid slot id count data"
                        hulcsDiff.put(parts[1] + "," + parts[2], parts[3]+" "+parts[4]+" "+parts[5]);
                        broadcast(line, id);
                    }
                    else if (command.equals("HULCS_ID")) {
                        nextHulcsId = Math.max(nextHulcsId, Integer.parseInt(parts[1]) + 1);
                        broadcast(line, id);
                    }
                    else if (command.equals("HIT")) {
                        // "HIT targetID damage"
                        int targetId = Integer.parseInt(parts[1]);
                        int damage = Integer.parseInt(parts[2]);
                        sendTo(targetId, "DAMAGE " + damage);
                    }
                }
            } catch (IOException e) {
                System.out.println("Player " + id + " disconnected.");
            } finally {
                synchronized (clients) {
                    clients.remove(id);
                }
            }
        }

        // Send a message to a specific player
        private void sendTo(int targetId, String msg) {
            synchronized (clients) {
                PrintWriter pw = clients.get(targetId);
                if (pw != null) pw.println(msg);
            }
        }

        // Send a message to all players (except maybe the sender)
        private void broadcast(String msg, int excludeId) {
            synchronized (clients) {
                for (Map.Entry<Integer, PrintWriter> entry : clients.entrySet()) {
                    if (entry.getKey() != excludeId) {
                        entry.getValue().println(msg);
                    }
                }
            }
        }
    }
}