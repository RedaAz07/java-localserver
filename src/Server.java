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

            // 3. Loop through every port for this specific server
            for (int port : config.getPorts()) {

                // Open a new channel (highway) for this port
                ServerSocketChannel serverChannel = ServerSocketChannel.open();

                // CRITICAL: Tell the OS this channel is non-blocking!
                serverChannel.configureBlocking(false);

                // Bind it to the specific IP and Port
                serverChannel.bind(new InetSocketAddress(host, port));

                // Register this channel with our Selector.
                // OP_ACCEPT means: "Wake me up when a new browser tries to connect."
                serverChannel.register(selector, SelectionKey.OP_ACCEPT);

                System.out.println("Started virtual server on " + host + ":" + port);
            }
        }

        System.out.println("All ports bound. Entering the event loop...");
        runEventLoop();
    }

    private void runEventLoop() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        // This is your single thread holding the entire server alive
        while (true) {

            // This method blocks until at least one event (red flag) happens on a channel
            selector.select();

            // Get the list of channels that are ready for action
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();

                // CRITICAL: You must remove the key from the iterator,
                // otherwise the selector will try to process the same event again on the next
                // loop!
                keys.remove();

                if (!key.isValid()) {
                    continue;
                }

                // Check what kind of event woke us up
                if (key.isAcceptable()) {
                    // A new browser is knocking on the door
                    acceptConnection(key);
                } else if (key.isReadable()) {
                    // A browser is sending us an HTTP request (GET, POST, etc.)
                    readRequest(key, buffer);
                } else if (key.isWritable()) {
                    // We are ready to send our HTTP response back to the browser
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