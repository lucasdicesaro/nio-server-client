import java.nio.channels.*;

public class Client {
    private int id;
    private String name;
    private SocketChannel socketChannel;

    public Client(int id, String name, SocketChannel socketChannel) {
        this.id = id;
        this.name = name;
        this.socketChannel = socketChannel;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    @Override
    public String toString() {
        return "NOD:" + id + "|NAME:" + name;
    }
}
