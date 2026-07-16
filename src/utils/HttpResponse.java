package utils;

import java.nio.ByteBuffer;
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

    public void setStatusCode(int statusCode, String statusMessage) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
    }

    public void setHeader(String key, String value) {
        headers.put(key, value);
    }

    public void setBody(byte[] body) {
        this.body = body;
        setHeader("Content-Length", String.valueOf(body.length));
    }

    /**
     * Add a Set-Cookie header to the response (multiple cookies can be set).
     */
    public void addSetCookie(Cookie cookie) {
        setCookies.add(cookie);
    }

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
}