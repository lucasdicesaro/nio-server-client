import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

public class NioServer {

    private static final int PORT = 12345;
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private final Set<Client> clients = new HashSet<>();
    private int clientIdSequence = 0;

    public static void main(String[] args) throws IOException {
        new NioServer().start();
    }

    public void start() throws IOException {
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new java.net.InetSocketAddress(PORT));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        System.out.println("Chat server started on port " + PORT);

        while (true) {
            selector.select();
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                keyIterator.remove();

                if (key.isAcceptable()) {
                    acceptConnection();
                } else if (key.isReadable()) {
                    handleClientMessage(key);
                }
            }
        }
    }

    private void acceptConnection() throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
        clients.add(new Client(clientIdSequence++, "noname", clientChannel));
        System.out.println("Client connected: " + clientChannel.getRemoteAddress());
        broadcastMessage("A new client has joined the chat.");
    }

    private void closeConnection(SocketChannel clientChannel) throws IOException {
        // clients.removeIf(c -> c.getSocketChannel().equals(clientChannel));
        Client client = clients.stream()
                .filter(c -> c.getSocketChannel().equals(clientChannel))
                .findAny()
                .orElse(null);

        System.out.println("Client disconnected: " + clientChannel.getRemoteAddress());
        clientChannel.close();
        clients.remove(client);
        broadcastMessage(client.getName() + " has left the chat.");
    }

    private void handleClientMessage(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(256);

        int bytesRead = 0;
        try {
            bytesRead = clientChannel.read(buffer);
        } catch (java.net.SocketException e) {
            closeConnection(clientChannel);
            return;
        }

        if (bytesRead == -1) {
            closeConnection(clientChannel);
            return;
        }

        buffer.flip();
        String message = new String(buffer.array(), 0, buffer.limit()).trim();
        if (message.equalsIgnoreCase(Commands.LIST_CLIENTS)) {
            listClients(clientChannel);
        } else if (message.startsWith(Commands.CHANGE_NAME)) {
            changeName(message, clientChannel);
        } else {
            System.out.println("Received message: " + message);
            broadcastMessage(message);
        }
    }

    private void broadcastMessage(String message) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());

        for (Client client : clients) {
            buffer.rewind();
            client.getSocketChannel().write(buffer);
        }
    }

    private void listClients(SocketChannel requestingClient) throws IOException {
        StringBuilder clientsList = new StringBuilder("Connected clients:\n");
        for (Client client : clients) {
            clientsList.append(client.getName() + " " + client.getSocketChannel().getRemoteAddress()).append("\n");
        }

        sendMessage(requestingClient, clientsList.toString());
    }

    private void changeName(String message, SocketChannel requestingClient) throws IOException {
        Client client = clients.stream()
                .filter(c -> c.getSocketChannel().equals(requestingClient))
                .findAny()
                .orElse(null);

        String name = removeCommandFromMessage(Commands.CHANGE_NAME, message);
        client.setName(name);
        sendMessage(requestingClient, "Name " + name + " applied");
    }

    private void sendMessage(SocketChannel requestingClient, String message) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
        requestingClient.write(buffer);
    }

    private String removeCommandFromMessage(String command, String message) {
        return message.replace(command, "");
    }

    private void shutdown() {
        System.out.println("Close all client channels");
        try {
            // Close all client channels
            for (Client client : clients) {
                SocketChannel channel = client.getSocketChannel();
                if (channel != null && channel.isOpen()) {
                    channel.close();
                }
            }
            clients.clear();

            // Close the server socket channel
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close();
            }

            // Close the selector
            // if (selector != null && selector.isOpen()) {
            // selector.close();
            // }
        } catch (Exception e) {
            // The server is shutdown. Ignoring errors.
            e.printStackTrace();
        }
    }
}
