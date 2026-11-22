package app.wordquest;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

// 인터넷: 새 버전 확인(GitHub Pages)만. 게임 기록은 폰 안에만 있고 어디에도 보내지 않는다.
final class Net {
    // APK·version.json이 있는 곳
    static final String BASE = "https://ggorotls-prog.github.io/bible-word-quest";

    static final class Result {
        final int status;
        final String body;
        Result(int status, String body) { this.status = status; this.body = body; }
    }

    private Net() {}

    static Result get(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(15_000);
        try {
            int code = c.getResponseCode();
            InputStream s = code < 400 ? c.getInputStream() : c.getErrorStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            if (s != null) {
                byte[] b = new byte[8192];
                int n;
                while ((n = s.read(b)) > 0) buf.write(b, 0, n);
            }
            return new Result(code, buf.toString(StandardCharsets.UTF_8.name()));
        } finally {
            c.disconnect();
        }
    }
}
