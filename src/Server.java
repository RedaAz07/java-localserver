import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import utils.ClientState;
import utils.HttpRequest;
import utils.HttpResponse;
import utils.RequestParser;
import utils.ResponseBuilder;
import utils.RouteConfig;
import utils.ServerConfig;
import utils.Session;

public class Server {
    private final List<ServerConfig> serverConfigs;
    private List<RouteConfig> routeConfigs;
    private Selector selector;
    private static final long DEFAULT_REQ_LIMIT = 5242880; // 5MB default, overridable per-route
    private static final int READ_BUFFER_SIZE = 65536;     // 64KB read buffer (was 1KB)

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
        ByteBuffer buffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);

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
            closeClient(key, client, state);
            return;
        }

        buffer.flip();

        // --- STREAMING PATH: body goes directly to a temp file ---
        if (state.isStreamingBody) {
            // Write directly to the temp file — never accumulates in memory
            while (buffer.hasRemaining()) {
                int remaining = buffer.remaining();
                long needed = getExpectedBodySize(state) - state.bodyBytesWritten;
                if (needed <= 0) break;

                int toWrite = (int) Math.min(remaining, needed);
                state.bodyOutputStream.write(buffer.array(),
                        buffer.arrayOffset() + buffer.position(), toWrite);
                buffer.position(buffer.position() + toWrite);
                state.bodyBytesWritten += toWrite;
            }

            if (state.bodyBytesWritten >= getExpectedBodySize(state)) {
                finishStreamingRequest(state);
                state.isRequestComplete = true;
                buildAndQueueResponse(key, state);
            }
            return;
        }

        // --- HEADER / SMALL BODY PATH: accumulate in ByteArrayOutputStream ---
        byte[] data = new byte[buffer.limit()];
        buffer.get(data);
        state.buffer.write(data);

        // Only convert to byte array for header parsing (not on every chunk for body)
        byte[] allDataSoFar = state.buffer.toByteArray();

        if (!state.isHeadersParsed) {
            int headerEnd = RequestParser.findHeaderEndIndex(allDataSoFar);

            if (headerEnd != -1) {
                state.headerLength = headerEnd;
                HttpRequest parsedReq = RequestParser.parseRequest(allDataSoFar);
                if (parsedReq != null) {
                    state.request = parsedReq;
                    state.isHeadersParsed = true;

                    state.matchedRoute = Router.matchRoute(state.request.getPath(), routeConfigs);

                    // Check limit BEFORE deciding to stream
                    if (isBodyTooLarge(state)) {
                        state.isError = true;
                        state.isRequestComplete = true;
                        buildAndQueueResponse(key, state);
                        return;
                    }

                    // Decide: should we stream the body to disk?
                    long expectedBody = getExpectedBodySize(state);
                    // Stream to file if body > 1MB or if it's a POST/PUT
                    if (expectedBody > 1048576 &&
                            ("POST".equals(state.request.getMethod())
                                    || "PUT".equals(state.request.getMethod()))) {
                        startStreamingBody(state, allDataSoFar);
                        // If body already complete in initial chunk, finish now
                        if (state.bodyBytesWritten >= expectedBody) {
                            finishStreamingRequest(state);
                            state.isRequestComplete = true;
                            buildAndQueueResponse(key, state);
                        }
                        return;
                    }
                }
            }
        }

        if (state.isHeadersParsed && !state.isRequestComplete) {
            checkIfBodyIsComplete(state, allDataSoFar);
        }

        if (state.isRequestComplete) {
            buildAndQueueResponse(key, state);
        }
    }

    /**
     * Switch to streaming mode: create a temp file and write any body data
     * already accumulated past the headers.
     */
    private void startStreamingBody(ClientState state, byte[] allDataSoFar) throws IOException {
        state.bodyFilePath = Files.createTempFile("upload-", ".tmp");
        state.bodyOutputStream = Files.newOutputStream(state.bodyFilePath);
        state.isStreamingBody = true;

        // Write any body bytes already accumulated after headers
        int bodyStart = state.headerLength;
        if (bodyStart < allDataSoFar.length) {
            int alreadyRead = allDataSoFar.length - bodyStart;
            state.bodyOutputStream.write(allDataSoFar, bodyStart, alreadyRead);
            state.bodyBytesWritten = alreadyRead;
        }

        // Free the header buffer — no longer needed
        state.buffer = null;
    }

    /**
     * Close the temp file stream and set the path on the request.
     */
    private void finishStreamingRequest(ClientState state) throws IOException {
        if (state.bodyOutputStream != null) {
            state.bodyOutputStream.close();
            state.bodyOutputStream = null;
        }
        if (state.bodyFilePath != null) {
            state.request.setBodyFilePath(state.bodyFilePath);
        }
    }

    /**
     * Get expected Content-Length, or 0 if not present.
     */
    private long getExpectedBodySize(ClientState state) {
        if (state.request == null) return 0;
        String cl = state.request.getHeader("Content-Length");
        if (cl != null) {
            try { return Long.parseLong(cl); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    /**
     * Check whether the body exceeds the configured limit.
     */
    private boolean isBodyTooLarge(ClientState state) {
        String contentLengthStr = state.request.getHeader("Content-Length");
        if (contentLengthStr == null) return false;

        long contentLength = Long.parseLong(contentLengthStr);
        long limit = getEffectiveBodyLimit(state);
        return contentLength > limit;
    }

    /**
     * Get the effective body limit: route-specific > server-default.
     */
    private long getEffectiveBodyLimit(ClientState state) {
        if (state.matchedRoute != null && state.matchedRoute.getClientBodyLimit() != null) {
            return state.matchedRoute.getClientBodyLimit();
        }
        return DEFAULT_REQ_LIMIT;
    }

    private void buildAndQueueResponse(SelectionKey key, ClientState state) {
        HttpResponse response;

        if (state.isError) {
            Map<String, String> errorPages = state.matchedRoute != null
                    ? state.matchedRoute.getErrorPages() : null;
            response = ResponseBuilder.buildErrorResponse(413, "Payload Too Large", errorPages);
        } else {
            response = ResponseBuilder.build(state.request, state.matchedRoute);
        }

        // --- session handling ---
        state.session = Session.fromRequest(state.request);
        if (state.session == null) {
            state.session = Session.create();
        }
        response.addSetCookie(state.session.toCookie());

        // If the response has a file body, set up streaming
        if (response.hasFileBody()) {
            try {
                state.responseFileChannel = FileChannel.open(response.getFilePath(),
                        StandardOpenOption.READ);
                state.responseFileBytesSent = 0;
                state.isStreamingFileResponse = true;
            } catch (IOException e) {
                System.err.println("Failed to open file for streaming: " + e.getMessage());
                response = ResponseBuilder.buildErrorResponse(500, "Internal Server Error", null);
                state.isStreamingFileResponse = false;
            }
        }

        state.responseBuffer = response.toByteBuffer();
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void sendResponse(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ClientState state = (ClientState) key.attachment();

        // --- Phase 1: Send the header (and small body if not streaming) ---
        if (state.responseBuffer != null) {
            client.write(state.responseBuffer);

            if (state.responseBuffer.hasRemaining()) {
                return; // still sending headers
            }

            // Headers sent. If streaming a file, switch to phase 2.
            if (state.isStreamingFileResponse && state.responseFileChannel != null) {
                state.responseBuffer = null; // headers done, switch to file streaming
                // fall through to phase 2 below
            } else {
                // No file to stream — response fully sent
                state.cleanupBodyFile();
                key.attach(new ClientState());
                key.interestOps(SelectionKey.OP_READ);
                return;
            }
        }

        // --- Phase 2: Stream the file body in chunks ---
        if (state.isStreamingFileResponse && state.responseFileChannel != null) {
            ByteBuffer chunk = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
            int bytesRead = state.responseFileChannel.read(chunk);

            if (bytesRead > 0) {
                chunk.flip();
                while (chunk.hasRemaining()) {
                    client.write(chunk);
                }
                state.responseFileBytesSent += bytesRead;
            }

            if (bytesRead <= 0) {
                // File fully streamed
                state.cleanupBodyFile();
                key.attach(new ClientState());
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    private void closeClient(SelectionKey key, SocketChannel client, ClientState state) throws IOException {
        if (state != null) {
            state.cleanupBodyFile();
        }
        key.cancel();
        client.close();
    }

    private void checkIfBodyIsComplete(ClientState state, byte[] allDataSoFar) {
        if (state.isError)
            return;

        String contentLengthStr = state.request.getHeader("Content-Length");
        if (contentLengthStr != null) {
            long expectedBodySize = Long.parseLong(contentLengthStr);
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