package utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClientState {
    public ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    public HttpRequest request = null;
    public RouteConfig matchedRoute = null;
    public Session session = null;
    public boolean isHeadersParsed = false;
    public boolean isRequestComplete = false;
    public boolean isError = false;
    public int headerLength = 0;
    public ByteBuffer responseBuffer = null;

    // --- streaming upload fields ---
    public Path bodyFilePath = null;           // temp file for streaming large request bodies
    public OutputStream bodyOutputStream = null; // stream to that temp file
    public long bodyBytesWritten = 0;          // how many body bytes written so far
    public boolean isStreamingBody = false;    // true once we switch to streaming mode

    // --- streaming file response fields ---
    public FileChannel responseFileChannel = null; // open channel for streaming a file response
    public long responseFileBytesSent = 0;         // bytes sent so far
    public boolean isStreamingFileResponse = false; // true when sending a large file body

    /**
     * Close the body output stream if open, and delete the temp file on error.
     * Also closes any open response file channel.
     */
    public void cleanupBodyFile() {
        if (bodyOutputStream != null) {
            try { bodyOutputStream.close(); } catch (IOException ignored) {}
            bodyOutputStream = null;
        }
        if (bodyFilePath != null) {
            try { Files.deleteIfExists(bodyFilePath); } catch (IOException ignored) {}
            bodyFilePath = null;
        }
        if (responseFileChannel != null) {
            try { responseFileChannel.close(); } catch (IOException ignored) {}
            responseFileChannel = null;
        }
    }
}