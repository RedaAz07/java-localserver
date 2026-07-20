package utils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class ClientState {
    public enum ChunkState {
        READING_SIZE,
        READING_DATA,
        READING_CRLF,
        FINISHED
    }

    public boolean useDisk = false;
    public ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    public HttpRequest request = null;
    public RouteConfig matchedRoute = null;
    public Session session = null;
    public boolean isHeadersParsed = false;
    public boolean isRequestComplete = false;
    public Path tempFilePath = null;
    public FileChannel fileChannel = null;
    public boolean isError = false;
    public int headerLength = 0;
    public ByteBuffer responseBuffer = null;
    public int errorCode = 0;
    public String errorMessage = "";
    public long lastActivityMillis = System.currentTimeMillis();
    public long bytesWritten = 0;

    public int currentChunkBytesRead = 0;
    public boolean isChunked = false;
    public ChunkState chunkState = ChunkState.READING_SIZE;
    public int currentChunkSize = 0;
    public int chunkBytesRead = 0;
    public ByteArrayOutputStream chunkLineBuilder = new ByteArrayOutputStream();
}