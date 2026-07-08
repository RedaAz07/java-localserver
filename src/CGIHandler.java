import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import utils.HttpRequest;
import utils.HttpResponse;

public class CGIHandler {

    public static HttpResponse handle(HttpRequest request, File scriptFile, String scriptPath, String queryString)
            throws Exception {
        ProcessBuilder builder = new ProcessBuilder("python3", scriptFile.getAbsolutePath());
        Map<String, String> env = builder.environment();

        env.put("GATEWAY_INTERFACE", "CGI/1.1");
        env.put("SERVER_PROTOCOL", request.getVersion() != null ? request.getVersion() : "HTTP/1.1");
        env.put("REQUEST_METHOD", request.getMethod() != null ? request.getMethod() : "");
        env.put("SCRIPT_NAME", scriptPath);
        env.put("PATH_INFO", scriptFile.getAbsolutePath());
        env.put("QUERY_STRING", queryString != null ? queryString : "");

        String contentType = request.getHeader("Content-Type");
        if (contentType != null) {
            env.put("CONTENT_TYPE", contentType);
        }
        byte[] body = request.getBody();
        env.put("CONTENT_LENGTH", String.valueOf(body != null ? body.length : 0));

        // Forward every request header as HTTP_<NAME> (CGI/1.1 convention).
        for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            String key = "HTTP_" + header.getKey().toUpperCase().replace('-', '_');
            env.put(key, header.getValue());
        }

        Process process = builder.start();

        InputStream in = process.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }

        process.waitFor();

        HttpResponse response = new HttpResponse();
        response.setHeader("Content-Type", "text/html");
        response.setBody(out.toByteArray());
        return response;
    }
}