import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class Router {
    public static void handleRoute(SocketChannel client, String path) throws IOException {
        String responseBody = "";
        int statusCode = 200;
        String statusMessage = "OK";

        // تحديد المحتوى بناءً على المسار
        if (path.equals("/")) {
            // هنا يمكنك قراءة محتوى ملف index.html من المسار المحدد في config.json
            responseBody = "<html><body><h1>Welcome Home!</h1><p>This is the default index page.</p></body></html>";
        } else if (path.equals("/about")) {
            responseBody = "<html><body><h1>About Us</h1><p>ahmed's Custom Java Server</p></body></html>";
        } else {
            statusCode = 404;
            statusMessage = "Not Found";
            responseBody = "<html><body><h1>404 - Page Not Found</h1></body></html>";
        }

        // 3. بناء استجابة HTTP قياسية (HTTP Response Format)
        // يجب أن تفصل بـ \r\n بين الأسطر، وبسطرين فارغين \r\n\r\n قبل محتوى الـ Body
        String httpResponse = "HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: " + responseBody.getBytes().length + "\r\n" +
                "Connection: close\r\n" +
                "\r\n" +
                responseBody;

        // إرسال الاستجابة للمتصفح
        ByteBuffer responseBuffer = ByteBuffer.wrap(httpResponse.getBytes());
        while (responseBuffer.hasRemaining()) {
            client.write(responseBuffer);
        }

        // إغلاق الاتصال بعد إرسال الرد
        client.close();
    }
}