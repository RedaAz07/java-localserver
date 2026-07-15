import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import utils.ClientState;
import utils.HttpRequest;
import utils.HttpResponse;
import utils.RequestParser;
import utils.ResponseBuilder;
import utils.RouteConfig;
import utils.ServerConfig;

public class Server {
    private final List<ServerConfig> serverConfigs;
    private List<RouteConfig> routeConfigs;
    private Selector selector;

    public Server(List<ServerConfig> serverConfigs) {
        this.serverConfigs = serverConfigs;
        this.routeConfigs = new java.util.ArrayList<>();
    }

    public void start() throws IOException {
        this.selector = Selector.open();

        for (ServerConfig config : serverConfigs) {
            this.routeConfigs.addAll(config.getRoutes());
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
        SocketChannel client = (SocketChannel) key.channel();
        ClientState state = (ClientState) key.attachment();
        if (state == null) {
            state = new ClientState();
            key.attach(state);
        }

        buffer.clear();
        int bytesRead = client.read(buffer);

        if (bytesRead == -1) {
            client.close();
            return;
        }

        buffer.flip();
        byte[] data = new byte[buffer.limit()];
        buffer.get(data);
        state.buffer.write(data);
        byte[] allDataSoFar = state.buffer.toByteArray();
System.out.println("NIO Read: Got+++++++++++++++++ " + bytesRead + " bytes. Total collected so far: " + allDataSoFar.length + " bytes.");
        if (!state.isHeadersParsed) {
            int headerEnd = RequestParser.findHeaderEndIndex(allDataSoFar);

            if (headerEnd != -1) {
                state.headerLength = headerEnd;
                HttpRequest parsedReq = RequestParser.parseRequest(allDataSoFar);

                if (parsedReq != null) {
                    state.request = parsedReq;
                    state.isHeadersParsed = true;

                    state.matchedRoute = Router.matchRoute(state.request.getPath(), routeConfigs);
                    checkBodyLimit(state);
                }
            }
        }

        if (state.isHeadersParsed && !state.isRequestComplete) {
            checkIfBodyIsComplete(state, allDataSoFar);
        }

        if (state.isRequestComplete) {
            HttpResponse response;

            if (state.isError) {
                response = ResponseBuilder.buildErrorResponse(413, "Payload Too Large");
            } else {
                response = ResponseBuilder.build(state.request, state.matchedRoute);
            }

            state.responseBuffer = response.toByteBuffer();
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }

   private void sendResponse(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ClientState state = (ClientState) key.attachment(); 

        if (state.responseBuffer != null) {
            client.write(state.responseBuffer);

            if (!state.responseBuffer.hasRemaining()) {
                key.attach(new ClientState());
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    private void checkBodyLimit(ClientState state) {
        String contentLengthStr = state.request.getHeader("Content-Length");

        if (contentLengthStr != null) {
            long contentLength = Long.parseLong(contentLengthStr);
            long limit = 5242880;
            if (state.matchedRoute != null) {
                limit = state.matchedRoute.getClientBodyLimit()== null ? 520042880 : state.matchedRoute.getClientBodyLimit();
            }
            if (contentLength > limit) {
                System.err.println("Payload Too Large! Limit: " + limit + ", Requested: " + contentLength);
                state.isError = true;
                state.isRequestComplete = true;
            }
        }
    }

    private void checkIfBodyIsComplete(ClientState state, byte[] allDataSoFar) {
        if (state.isError)
            return;

        String contentLengthStr = state.request.getHeader("Content-Length");

        if (contentLengthStr != null) {
            int expectedBodySize = Integer.parseInt(contentLengthStr);
            int currentBodySize = allDataSoFar.length - state.headerLength;

            if (currentBodySize >= expectedBodySize) {
                state.isRequestComplete = true;

                byte[] completeBody = Arrays.copyOfRange(allDataSoFar, state.headerLength, allDataSoFar.length);
                state.request.setBody(completeBody);
            }
        } else if ("chunked".equals(state.request.getHeader("Transfer-Encoding"))) {
            String dataStr = new String(allDataSoFar);
            if (dataStr.endsWith("0\r\n\r\n")) {
                state.isRequestComplete = true;
            }
        } else {
            state.isRequestComplete = true;
        }
    }

}