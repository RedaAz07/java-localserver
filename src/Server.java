import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import utils.ServerConfig;

public class Server {
    private final List<ServerConfig> serverConfigs;
    private Selector selector;

    public Server(List<ServerConfig> serverConfigs) {
        this.serverConfigs = serverConfigs;
    }

    public void start() throws IOException {
        // 1. Open the grand traffic controller
        this.selector = Selector.open();

        // 2. Loop through every server block in your config
        for (ServerConfig config : serverConfigs) {
            String host = config.getHost();

            for (int port : config.getPorts()) {

                try {
                    ServerSocketChannel serverChannel = ServerSocketChannel.open();

                    serverChannel.configureBlocking(false);
                    serverChannel.bind(new InetSocketAddress(host, port));

                    serverChannel.register(selector, SelectionKey.OP_ACCEPT);

                    System.out.println("Started virtual server on " + host + ":" + port);
                } catch (Exception e) {
                    System.err
                            .println("Failed to start virtual server on " + host + ":" + port + " - " + e.getMessage());
                    continue;
                }
            }
        }
        System.out.println("All ports bound. Entering the event loop...");
        runEventLoop();
    }

    private void runEventLoop() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        // This is your single thread holding the entire server alive
        while (true) {
            selector.select(1000);
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();

                keys.remove();

                if (!key.isValid()) {
                    continue;
                }

                if (key.isAcceptable()) {
                    acceptConnection(key);  
                } else if (key.isReadable()) {
                    // A browser is sending us an HTTP request (GET, POST, etc.)
                    readRequest(key, buffer);
                } else if (key.isWritable()) {
                    sendResponse(key);
                }
            }

        }
    }

    private void acceptConnection(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ);
        System.out.println("Accepted new connection from: " + client.getRemoteAddress());
    }

    private void readRequest(SelectionKey key, ByteBuffer buffer) throws IOException {
            // 4. Read the client request
            SocketChannel client = (SocketChannel) key.channel();
            buffer.clear();
            int bytesRead = client.read(buffer);

            if (bytesRead == -1) {
                client.close();
                return;
            }

            buffer.flip(); // Prepare buffer for reading

            // Print received data
            byte[] data = new byte[buffer.limit()];
            buffer.get(data);
            System.out.println("Received: " + new String(data).trim());

            // 5. Send the response back
            buffer.rewind(); // Rewind to write the same data back
            client.write(buffer);

            // Close the client channel after responding
            client.close();
    }

    private void sendResponse(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();

        // Attempt to write the remaining bytes to the socket
        client.write(buffer);

        // Check if there is still data to be sent (e.g., if the socket buffer got full)
        if (!buffer.hasRemaining()) {
            buffer.clear();
            // Switch interest back to reading new requests from this client
            key.interestOps(SelectionKey.OP_READ);
        }
    }
}