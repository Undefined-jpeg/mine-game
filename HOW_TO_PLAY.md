# How to Play with Friends over the Internet

To play this game with friends who are not on your local network, follow these steps:

## 1. Hosting the Server
One person needs to be the "Host".
1. Run `GameServer` (e.g., using `java GameServer`).
2. Look at the console output. You will see a **Public IP** address (e.g., `123.45.67.89`).
3. Copy this Public IP and send it to your friends.

## 2. Port Forwarding (CRITICAL)
For your friends to connect, your router must allow traffic on port **9999**.
1. Log into your router's admin panel (usually `192.168.1.1` or `192.168.0.1`).
2. Find the **Port Forwarding** or **Virtual Server** section.
3. Create a new rule:
   - **Port:** 9999
   - **Protocol:** TCP
   - **Internal IP:** Your PC's Local IP (the "🏠 Local IP" shown in the server console).
4. Save the settings.

## 3. Joining the Game
1. Run `GameClient` (e.g., using `java GameClient`).
2. When prompted for the "Server IP", enter the **Public IP** provided by the host.
3. Choose your color and start playing!

## Troubleshooting
- **Cannot connect?** Ensure the host's firewall (Windows Firewall, etc.) isn't blocking Java or port 9999.
- **Public IP not shown?** Visit [https://checkip.amazonaws.com](https://checkip.amazonaws.com) to find it manually.
- **Still not working?** Some ISPs use CGNAT, which makes port forwarding impossible. In that case, use a tool like **Ngrok** or **Hamachi** to create a virtual network.
