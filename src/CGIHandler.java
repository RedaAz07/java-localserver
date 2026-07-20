import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import utils.HttpRequest;
import utils.HttpResponse;

public class CGIHandler {

    private static final long TIMEOUT_SECONDS = 10;

    private static final Map<String, String> INTERPRETERS = new HashMap<>();
    static {
        INTERPRETERS.put(".py", "python3");
        INTERPRETERS.put(".js", "node");
    }

    private static final String TEMP_FILE_HEADER = "Temp-File-Path";

    public static HttpResponse handle(HttpRequest request, File scriptFile, String scriptPath, String queryString)
            throws Exception {
        String interpreter = interpreterFor(scriptFile.getName());
        if (interpreter == null) {
            throw new IOException("No CGI interpreter configured for: " + scriptFile.getName());
        }

        ProcessBuilder builder = new ProcessBuilder(interpreter, scriptFile.getAbsolutePath());
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
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

        String tempFilePathHeader = request.getHeader(TEMP_FILE_HEADER);
        File stdinSourceFile;
        boolean ownsStdinFile;
        long contentLength;

        if (tempFilePathHeader != null) {
            stdinSourceFile = new File(tempFilePathHeader);
            contentLength = stdinSourceFile.exists() ? stdinSourceFile.length() : 0;
            ownsStdinFile = false; 
        } else {
            byte[] body = request.getBody();
            stdinSourceFile = File.createTempFile("cgi-stdin-", ".tmp");
            if (body != null && body.length > 0) {
                Files.write(stdinSourceFile.toPath(), body);
            }
            contentLength = body != null ? body.length : 0;
            ownsStdinFile = true;
        }
        env.put("CONTENT_LENGTH", String.valueOf(contentLength));

        for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            if (header.getKey().equalsIgnoreCase(TEMP_FILE_HEADER)) {
                continue;
            }
            String key = "HTTP_" + header.getKey().toUpperCase().replace('-', '_');
            env.put(key, header.getValue());
        }

        builder.redirectInput(stdinSourceFile);

        Process process = null;
        try {
            process = builder.start();

            InputStream in = process.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException(
                        "CGI script timed out after " + TIMEOUT_SECONDS + "s: " + scriptFile.getPath());
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                System.err.println("CGI script exited with code " + exitCode + ": " + scriptFile.getPath()
                        + " (script's stderr, if any, was printed above)");
                throw new IOException("CGI script exited with code " + exitCode + ": " + scriptFile.getPath());
            }

            return parseCgiOutput(out.toByteArray());

        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (ownsStdinFile) {
                stdinSourceFile.delete();
            }
        }
    }

    private static String interpreterFor(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1) {
            return null;
        }
        return INTERPRETERS.get(fileName.substring(dot).toLowerCase());
    }

    private static HttpResponse parseCgiOutput(byte[] output) {
        HttpResponse response = new HttpResponse();

        String raw = new String(output);
        String headerBlock;
        String bodyStr;

        int sepIdx = raw.indexOf("\r\n\r\n");
        int sepLen = 4;
        if (sepIdx == -1) {
            sepIdx = raw.indexOf("\n\n");
            sepLen = 2;
        }

        if (sepIdx == -1) {
            headerBlock = "";
            bodyStr = raw;
        } else {
            headerBlock = raw.substring(0, sepIdx);
            bodyStr = raw.substring(sepIdx + sepLen);
        }

        int statusCode = 200;
        String statusMessage = "OK";
        boolean contentTypeSet = false;

        if (!headerBlock.isEmpty()) {
            for (String line : headerBlock.split("\r?\n")) {
                int colon = line.indexOf(':');
                if (colon == -1) {
                    continue;
                }
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();

                if (key.equalsIgnoreCase("Status")) {
                    String[] parts = value.split(" ", 2);
                    try {
                        statusCode = Integer.parseInt(parts[0]);
                    } catch (NumberFormatException ignored) {
                    }
                    statusMessage = parts.length > 1 ? parts[1] : statusMessage;
                } else {
                    if (key.equalsIgnoreCase("Content-Type")) {
                        contentTypeSet = true;
                    }
                    response.setHeader(key, value);
                }
            }
        }

        if (!contentTypeSet) {
            response.setHeader("Content-Type", "text/html; charset=UTF-8");
        }

        response.setStatusCode(statusCode, statusMessage);
        response.setBody(bodyStr.getBytes());
        return response;
    }
}