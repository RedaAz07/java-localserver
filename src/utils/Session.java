package utils;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Session {
    private static final Map<String, Session> store = new ConcurrentHashMap<>();

    public static final String COOKIE_NAME = "SESSID";

    /**
     * Create a new session, store it, and return it.
     */
    public static Session create() {
        Session s = new Session();
        store.put(s.getId(), s);
        return s;
    }

    /**
     * Look up a session by ID, update last-access time, or return null.
     */
    public static Session get(String id) {
        if (id == null) return null;
        Session s = store.get(id);
        if (s != null) {
            s.touch();
        }
        return s;
    }

    /**
     * Remove a session from the store.
     */
    public static void remove(String id) {
        if (id != null) store.remove(id);
    }

    /**
     * Get a session from an HttpRequest's cookies, or null if none.
     */
    public static Session fromRequest(HttpRequest req) {
        Cookie c = req.getCookie(COOKIE_NAME);
        if (c == null) return null;
        return get(c.getValue());
    }

    private final String id;
    private final Map<String, String> data = new ConcurrentHashMap<>();
    private volatile long lastAccessMillis;

    public Session() {
        this.id = UUID.randomUUID().toString();
        this.touch();
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
        this.lastAccessMillis = Instant.now().toEpochMilli();
    }

    /**
     * Create a Cookie that carries this session's ID.
     */
    public Cookie toCookie() {
        return new Cookie(COOKIE_NAME, id);
    }
}