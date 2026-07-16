package utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpRequest {
    private String method;
    private String path;
    private String version;

    private Map<String, String> headers;

    private byte[] body;

    public HttpRequest() {
        this.headers = new HashMap<>();
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void addHeader(String key, String value) {
        this.headers.put(key, value);
    }

    public String getHeader(String key) {
        // Case-insensitive lookup (HTTP headers are case-insensitive)
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    /**
     * Parse the "Cookie" request header into a list of Cookie objects.
     */
    public List<Cookie> getCookies() {
        List<Cookie> cookies = new ArrayList<>();
        String cookieHeader = getHeader("Cookie");
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return cookies;
        }
        String[] parts = cookieHeader.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            int eqIdx = trimmed.indexOf('=');
            if (eqIdx == -1) continue;
            String name = trimmed.substring(0, eqIdx).trim();
            String value = trimmed.substring(eqIdx + 1).trim();
            cookies.add(new Cookie(name, value));
        }
        return cookies;
    }

    /**
     * Get a single cookie by name from the request, or null if not found.
     */
    public Cookie getCookie(String name) {
        for (Cookie c : getCookies()) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    public HttpRequest(String method, String path, String version, Map<String, String> headers, byte[] body) {
        this.method = method;
        this.path = path;
        this.version = version;
        this.headers = headers != null ? headers : new HashMap<>();
        this.body = body;
    }
}