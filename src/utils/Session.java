package utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Session {
    private static final Map<String, Session> store = new ConcurrentHashMap<>();
    private static final long SESSION_TIMEOUT_MILLIS = 30 * 60 * 1000;
    public static final String COOKIE_NAME = "SESSID";

    public static void cleanupExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(entry -> {
            Session s = entry.getValue();
            return (now - s.lastAccessMillis) > SESSION_TIMEOUT_MILLIS;
        });
    }

    public static Session create() {
        Session s = new Session();
        store.put(s.getId(), s);
        return s;
    }

    public static Session get(String id) {
        if (id == null)
            return null;
        Session s = store.get(id);
        if (s != null) {
            s.touch();
        }
        return s;
    }

    public static void remove(String id) {
        if (id != null)
            store.remove(id);
    }

    public static Session fromRequest(HttpRequest req) {
        Cookie c = req.getCookie(COOKIE_NAME);
        if (c == null)
            return null;
        return get(c.getValue());
    }

    private final String id;
    private final Map<String, String> data = new ConcurrentHashMap<>();
    private volatile long lastAccessMillis;

    public Session() {
        this.id = UUID.randomUUID().toString();
        this.lastAccessMillis = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public Map<String, String> getData() {
        return data;
    }

    public long getLastAccessMillis() {
        return lastAccessMillis;
    }

    public void touch() {
        this.lastAccessMillis = System.currentTimeMillis();
    }

    public Cookie toCookie() {
        return new Cookie(COOKIE_NAME, id);
    }
}
