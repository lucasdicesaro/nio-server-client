import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class NioClient {

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) throws IOException {

        SocketChannel clientChannel = SocketChannel.open();
        clientChannel.configureBlocking(false);

        Selector selector = Selector.open();
        clientChannel.connect(new java.net.InetSocketAddress(SERVER_ADDRESS, SERVER_PORT));

        // Register for connect event
        clientChannel.register(selector, SelectionKey.OP_CONNECT);

        new Thread(() -> {
            try {
                while (true) {
                    selector.select();
                    var selectedKeys = selector.selectedKeys();
                    var keyIterator = selectedKeys.iterator();

                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove();

                        if (key.isConnectable()) {
                            handleConnect(clientChannel, selector);
                        } else if (key.isReadable()) {
                            handleRead(clientChannel);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // Send messages from the keyboard
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                String message = scanner.nextLine();
                sendMessage(clientChannel, message);
            }
        } catch (NoSuchElementException e) {
            System.out.println("Disconnected by user");
        } finally {
            // Ctrl+C
            System.out.println("Closing socket.");
            clientChannel.close();
        }
    }

    private static void handleConnect(SocketChannel clientChannel, Selector selector) throws IOException {
        try {
            if (clientChannel.finishConnect()) {
                clientChannel.register(selector, SelectionKey.OP_READ);
                System.out.println("Connected to the server.");
            }
        } catch (ConnectException e) {
            System.out.println("Couldn't connect to the server. Aborting...");
            System.exit(1);
        }
    }

    private static void handleRead(SocketChannel clientChannel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(256);
        try {
            int bytesRead = clientChannel.read(buffer);

            if (bytesRead > 0) {
                buffer.flip();
                String message = new String(buffer.array(), 0, buffer.limit());
                System.out.println("Received: " + message);
            }

            if (bytesRead == -1) {
                System.out.println("Server shutdown");
                System.exit(-1);
            }
        } catch (SocketException e) {
            System.out.println("Server shutdown");
            System.exit(-1);
        }
    }

    private static void sendMessage(SocketChannel clientChannel, String message) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
        clientChannel.write(buffer);
    }
}
