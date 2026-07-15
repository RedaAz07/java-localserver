package utils;

import java.util.HashMap;
import java.util.Map;

public class MimeTypeUtil {

    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        MIME_TYPES.put("html", "text/html; charset=UTF-8");
        MIME_TYPES.put("htm", "text/html; charset=UTF-8");
        MIME_TYPES.put("css", "text/css; charset=UTF-8");
        MIME_TYPES.put("csv", "text/csv; charset=UTF-8");
        MIME_TYPES.put("txt", "text/plain; charset=UTF-8");

        MIME_TYPES.put("js", "application/javascript; charset=UTF-8");
        MIME_TYPES.put("json", "application/json; charset=UTF-8");
        MIME_TYPES.put("xml", "application/xml; charset=UTF-8");
        MIME_TYPES.put("pdf", "application/pdf");

        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("ico", "image/x-icon");
        MIME_TYPES.put("webp", "image/webp");
    }

    public static String getMimeType(String filePath) {
        if (filePath == null) {
            return "application/octet-stream";
        }

        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex != -1 && lastDotIndex < filePath.length() - 1) {
            String extension = filePath.substring(lastDotIndex + 1).toLowerCase();
            return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
        }

        return "application/octet-stream";
    }
}