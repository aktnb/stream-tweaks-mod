package org.etwas.streamtweaks.twitch.auth;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

import static org.etwas.streamtweaks.StreamTweaks.LOGGER;

public final class LocalHttpCallbackServer implements AutoCloseable {
    private final HttpServer server;

    public LocalHttpCallbackServer(int port, String path, Consumer<Map<String, String>> callbackHandler) {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        server.createContext(path, exchange -> {
            var uri = exchange.getRequestURI();
            var query = uri.getRawQuery();
            LOGGER.info("Received callback request - URI: {}, Query: {}", uri, query);

            // クエリパラメータがあればそれを処理 (通常の場合)
            if (query != null && !query.isEmpty()) {
                var params = QueryString.parse(query);
                LOGGER.info("Parsed parameters: {}", params);

                var response = "<html><body>✅ You can close this window.</body></html>";
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                try (var os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                callbackHandler.accept(params);
            } else {
                // URLフラグメント（#以降）を取得するためのJavaScript
                var htmlResponse = """
                        <html>
                        <head><title>OAuth Callback</title></head>
                        <body>
                            <h1>Processing...</h1>
                            <script>
                                // URLフラグメントからパラメータを抽出
                                const fragment = window.location.hash.substring(1);
                                if (fragment) {
                                    // フラグメントをサーバーに送信
                                    const params = new URLSearchParams(fragment);
                                    const queryString = params.toString();

                                    // サーバーに再度リクエストを送信（今度はクエリパラメータとして）
                                    window.location.href = window.location.pathname + '?' + queryString;
                                } else {
                                    document.body.innerHTML = '<h1>❌ Authentication failed</h1>';
                                }
                            </script>
                        </body>
                        </html>
                        """;

                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, htmlResponse.getBytes(StandardCharsets.UTF_8).length);
                try (var os = exchange.getResponseBody()) {
                    os.write(htmlResponse.getBytes(StandardCharsets.UTF_8));
                }
            }
        });
    }

    public void start() {
        server.start();
        LOGGER.info("Local HTTP callback server started on port {}", server.getAddress().getPort());
    }

    public void stop(int delaySec) {
        server.stop(delaySec);
        LOGGER.info("Local HTTP callback server stopped");
    }

    @Override
    public void close() {
        stop(0);
    }

    static final class QueryString {
        static Map<String, String> parse(String q) {
            java.util.HashMap<String, String> m = new java.util.HashMap<>();
            if (q == null || q.isEmpty())
                return m;
            for (String kv : q.split("&")) {
                int i = kv.indexOf('=');
                String k = i >= 0 ? kv.substring(0, i) : kv;
                String v = i >= 0 ? kv.substring(i + 1) : "";
                m.put(urlDecode(k), urlDecode(v));
            }
            return m;
        }

        static String urlDecode(String s) {
            return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
