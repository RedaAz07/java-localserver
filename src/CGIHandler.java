import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import utils.HttpResponse;

public class CGIHandler {

    public static HttpResponse handle(File scriptFile) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("python3", scriptFile.getAbsolutePath());
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