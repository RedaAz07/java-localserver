package utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpResponse {
    private int statusCode = 200;
    private String statusMessage = "OK";
    private Map<String, String> headers = new HashMap<>();
    private List<Cookie> setCookies = new ArrayList<>();
    private byte[] body = new byte[0];

    // --- streaming file response support ---
    private Path filePath = null;          // file to stream as the response body
    private long fileSize = 0;             // pre-computed file size

    public void setStatusCode(int statusCode, String statusMessage) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
    }

    public void setHeader(String key, String value) {
        headers.put(key, value);
    }

    public void setBody(byte[] body) {
        this.body = body;
        this.filePath = null; // clear any file streaming
        setHeader("Content-Length", String.valueOf(body.length));
    }

    /**
     * Set a file to serve as the response body. The file will be streamed
     * in chunks to avoid loading the entire file into memory.
     */
    public void setFileBody(Path filePath, long fileSize) {
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.body = new byte[0];
        setHeader("Content-Length", String.valueOf(fileSize));
    }

    /**
     * Add a Set-Cookie header to the response (multiple cookies can be set).
     */
    public void addSetCookie(Cookie cookie) {
        setCookies.add(cookie);
    }

    /**
     * Build the response header bytes + small body as a single ByteBuffer.
     * For large file responses set via setFileBody(), this returns only the
     * headers — the caller should use streamFileBody() to send the file content.
     */
    public ByteBuffer toByteBuffer() {
        StringBuilder responseString = new StringBuilder();
        responseString.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusMessage).append("\r\n");

        for (Map.Entry<String, String> header : headers.entrySet()) {
            responseString.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }

        for (Cookie cookie : setCookies) {
            responseString.append("Set-Cookie: ").append(cookie.toHeader()).append("\r\n");
        }

        responseString.append("\r\n");

        byte[] headerBytes = responseString.toString().getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(headerBytes.length + body.length);
        buffer.put(headerBytes);
        buffer.put(body);
        buffer.flip();

        return buffer;
    }

    /**
     * Returns true if this response has a file body to stream (set via
     * setFileBody).
     */
    public boolean hasFileBody() {
        return filePath != null;
    }

    /**
     * Returns the file path for streaming, or null.
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * Returns the pre-computed file size for Content-Length.
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Stream the file body to the given channel using a direct buffer.
     * Call this AFTER sending the header ByteBuffer from toByteBuffer().
     *
     * @param channel   the writable channel (e.g. SocketChannel)
     * @param chunkSize size of each write chunk (e.g. 65536 for 64KB)
     * @return true when the entire file has been sent
     */
    public boolean streamFileTo(WritableByteChannel channel, int chunkSize) throws IOException {
        if (filePath == null) return true;

        try (FileChannel fc = FileChannel.open(filePath, StandardOpenOption.READ)) {
            ByteBuffer chunk = ByteBuffer.allocateDirect(chunkSize);
            while (fc.read(chunk) > 0) {
                chunk.flip();
                while (chunk.hasRemaining()) {
                    channel.write(chunk);
                }
                chunk.clear();
            }
        }
        return true;
    }
}