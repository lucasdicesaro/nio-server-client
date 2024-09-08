import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

public class NioServer {

    private static final int PORT = 12345;
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private final Set<SocketChannel> clients = new HashSet<>();

    public static void main(String[] args) throws IOException {
        new NioServer().start();
    }

    public void start() throws IOException {
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new java.net.InetSocketAddress(PORT));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

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
        clients.add(clientChannel);
        System.out.println("Client connected: " + clientChannel.getRemoteAddress());
        broadcastMessage("A new client has joined the chat.");
    }

    private void closeConnection(SocketChannel clientChannel) throws IOException {
        clients.remove(clientChannel);
        System.out.println("Client disconnected: " + clientChannel.getRemoteAddress());
        clientChannel.close();
        broadcastMessage("A client has left the chat.");
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
        if (message.equalsIgnoreCase("/list_clients")) {
            listClients(clientChannel);
        } else {
            System.out.println("Received message: " + message);
            broadcastMessage(message);
        }
    }

    private void broadcastMessage(String message) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());

        for (SocketChannel client : clients) {
            buffer.rewind();
            client.write(buffer);
        }
    }

    private void listClients(SocketChannel requestingClient) throws IOException {
        StringBuilder clientsList = new StringBuilder("Connected clients:\n");
        for (SocketChannel client : clients) {
            clientsList.append(client.getRemoteAddress()).append("\n");
        }

        ByteBuffer buffer = ByteBuffer.wrap(clientsList.toString().getBytes());
        requestingClient.write(buffer);
    }
}
