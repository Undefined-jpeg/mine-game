import java.io.*;
import java.net.*;
import java.util.*;

public class GameServer {
    private static final Map<Integer, PrintWriter> clients = new HashMap<>();
    private static int nextPlayerId = 1;

    public static void main(String[] args) throws IOException {
        System.out.println("Starting Server on port " + SharedData.PORT + "...");
        try {
            System.out.println("Local IP: " + InetAddress.getLocalHost().getHostAddress());
        } catch (Exception e) {
            System.out.println("Could not determine local IP.");
        }

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

                // Tell the player their ID
                out.println("LOGIN " + id);

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
                    else if (command.equals("BLOCK")) {
                        // Block changed: "BLOCK x y type"
                        // Tell everyone to update their map
                        broadcast(line, -1); // -1 means send to everyone including sender
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