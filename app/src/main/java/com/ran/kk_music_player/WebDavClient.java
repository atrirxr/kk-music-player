package com.ran.kk_music_player;

import android.util.Base64;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class WebDavClient {

    private static final String TAG = "WebDAV";

    public static class CloudFile {
        public String name;
        public String href;
        public long size;
        public boolean isDirectory;
        public String modifiedDate;
        public String directUrl; // populated by CloudFragment for cache key matching

        private static final String[] AUDIO_EXTENSIONS = {
                ".mp3", ".wav", ".flac", ".ogg", ".aac", ".wma", ".m4a", ".opus"
        };

        public boolean isAudioFile() {
            if (isDirectory || name == null) return false;
            String lower = name.toLowerCase();
            for (String ext : AUDIO_EXTENSIONS) {
                if (lower.endsWith(ext)) return true;
            }
            return false;
        }

        public String getFormattedSize() {
            if (size < 1024) return size + " B";
            else if (size < 1024 * 1024) return String.format(Locale.US, "%.1f KB", size / 1024.0);
            else return String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0));
        }
    }

    private static final int TIMEOUT = (int) TimeUnit.SECONDS.toMillis(30);
    public static final int TIMEOUT_MS = TIMEOUT;
    private static final String PROPFIND_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<d:propfind xmlns:d=\"DAV:\">\n" +
            "  <d:prop>\n" +
            "    <d:displayname/>\n" +
            "    <d:getcontenttype/>\n" +
            "    <d:getcontentlength/>\n" +
            "    <d:resourcetype/>\n" +
            "    <d:getlastmodified/>\n" +
            "  </d:prop>\n" +
            "</d:propfind>";

    private final String baseUrl;
    private final String authHeader;
    public String connectUrl;
    private String lastError;

    public WebDavClient(String serverUrl, String username, String password) {
        String url = serverUrl.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.baseUrl = url;
        this.connectUrl = url;
        String credentials = (username != null ? username : "") + ":" + (password != null ? password : "");
        this.authHeader = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
    }

    /** 测试连接，返回 HTTP 状态码。先尝试 OPTIONS（标准方法，始终可用），再试 PROPFIND */
    public int testConnection() throws IOException {
        String testUrl = baseUrl + "/";
        // 标准 HTTP 方法 OPTIONS 作为首选
        try {
            int code = testConnectionOptions();
            Log.d(TAG, "OPTIONS response: " + code);
            if (code == 200 || code == 207 || code == 301 || code == 302) return code;
        } catch (IOException e) {
            Log.d(TAG, "OPTIONS failed: " + e);
        }
        // 再试 PROPFIND（通过原始 socket）
        try {
            int code = testConnectionPropfind();
            Log.d(TAG, "PROPFIND response: " + code);
            if (code == 207 || code == 200) return code;
        } catch (IOException e) {
            Log.d(TAG, "PROPFIND failed: " + e);
            throw e; // PROPFIND 也失败则向上抛
        }
        return -1;
    }

    /** 使用 OPTIONS 方法测试连接（HttpURLConnection 原生支持） */
    private int testConnectionOptions() throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = openHttpUrlConnection(baseUrl + "/", "OPTIONS", true);
            return conn.getResponseCode();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 使用原始 socket 发送 PROPFIND 测试连接 */
    private int testConnectionPropfind() throws IOException {
        RawHttpResponse resp = executeRawRequest(baseUrl + "/", "PROPFIND", authHeader, PROPFIND_XML.getBytes("UTF-8"));
        return resp.statusCode;
    }

    /** 获取用于调试的连接 URL */
    public String getConnectUrl() {
        return connectUrl;
    }

    /**
     * 将服务器返回的绝对路径 href 转换为相对于 WebDAV 挂载点的路径。
     * 例如: baseUrl="https://host/dav", href="/dav/music/" → "/music/"
     */
    public String relativizePath(String href) {
        return WebDavPathUtils.relativizePath(href, getMountPath());
    }

    public boolean isSamePath(String path, String href) {
        return WebDavPathUtils.isSamePath(path, href, getMountPath());
    }

    private String getMountPath() {
        try {
            return new URL(baseUrl).getPath();
        } catch (Exception ignored) {
            return null;
        }
    }

    public String getLastError() {
        return lastError;
    }

    public List<CloudFile> listFiles(String path) throws IOException, XmlPullParserException {
        String url = baseUrl + path;
        try {
            RawHttpResponse resp = executeRawRequest(url, "PROPFIND", authHeader, PROPFIND_XML.getBytes("UTF-8"));
            if (resp.statusCode != 207) {
                lastError = "服务器返回 HTTP " + resp.statusCode;
                throw new IOException("WebDAV returned " + resp.statusCode);
            }
            String xml = new String(resp.body, "UTF-8");
            lastError = null;
            return parsePropfindResponse(xml, path);
        } catch (IOException e) {
            lastError = e.getLocalizedMessage();
            throw e;
        }
    }

    public String getDirectUrl(String filePath) {
        try {
            URL parsed = new URL(baseUrl);
            java.net.URI uri = new java.net.URI(
                    parsed.getProtocol(),
                    null,
                    parsed.getHost(),
                    parsed.getPort() > 0 ? parsed.getPort() : -1,
                    filePath.startsWith("/") ? filePath : "/" + filePath,
                    null,
                    null
            );
            return uri.toString();
        } catch (Exception e) {
            return baseUrl + (filePath.startsWith("/") ? filePath : "/" + filePath);
        }
    }

    public String getAuthUrl(String filePath) {
        try {
            URL parsed = new URL(baseUrl);
            String userInfo = "";
            if (authHeader != null && !authHeader.isEmpty()) {
                String decoded = new String(Base64.decode(authHeader.substring(6), Base64.NO_WRAP));
                userInfo = decoded.contains(":") ? decoded.substring(0, decoded.indexOf(':')) + ":" +
                        decoded.substring(decoded.indexOf(':') + 1) : decoded;
            }
            // 使用 URI 正确处理密码中的特殊字符（如 @ # 等）
            java.net.URI uri = new java.net.URI(
                    parsed.getProtocol(),
                    userInfo,
                    parsed.getHost(),
                    parsed.getPort() > 0 ? parsed.getPort() : -1,
                    filePath.startsWith("/") ? filePath : "/" + filePath,
                    null,
                    null
            );
            return uri.toString();
        } catch (Exception e) {
            return baseUrl + (filePath.startsWith("/") ? filePath : "/" + filePath);
        }
    }

    /** 获取用于 ExoPlayer 身份验证的请求头 */
    public java.util.Map<String, String> getAuthHeaders() {
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        if (authHeader != null && !authHeader.isEmpty()) {
            headers.put("Authorization", authHeader);
        }
        return headers;
    }

    /**
     * 下载音频文件头部（用于提取封面等元数据）。
     * 处理 Alist → CDN 的重定向链，只对 Alist 发送认证头。
     */
    public byte[] downloadAudioHeader(String filePath, int maxBytes) throws IOException {
        String url = (filePath.startsWith("http://") || filePath.startsWith("https://"))
                ? filePath : getDirectUrl(filePath);
        boolean needAuth = true;

        for (int i = 0; i < 5; i++) {
            URL u = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();

            if (conn instanceof HttpsURLConnection) {
                HttpsURLConnection https = (HttpsURLConnection) conn;
                try {
                    SSLContext sslContext = createLenientSslContext();
                    https.setSSLSocketFactory(sslContext.getSocketFactory());
                    https.setHostnameVerifier(LENIENT_HOSTNAME_VERIFIER);
                } catch (Exception ignored) {}
            }

            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setRequestProperty("User-Agent", "AndroidMusicPlayer/1.0");
            conn.setRequestProperty("Range", "bytes=0-" + (maxBytes - 1));
            conn.setInstanceFollowRedirects(false);

            if (needAuth && authHeader != null) {
                conn.setRequestProperty("Authorization", authHeader);
            }

            int code = conn.getResponseCode();

            if (code == 301 || code == 302 || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isEmpty()) break;
                url = location;
                needAuth = false;
                continue;
            }

            if (code == 200 || code == 206) {
                try {
                    try (InputStream is = conn.getInputStream();
                         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        byte[] buf = new byte[8192];
                        int len;
                        int total = 0;
                        while ((len = is.read(buf)) != -1 && total < maxBytes) {
                            baos.write(buf, 0, len);
                            total += len;
                        }
                        return baos.toByteArray();
                    }
                } finally {
                    conn.disconnect();
                }
            }

            conn.disconnect();
            throw new IOException("HTTP " + code);
        }

        throw new IOException("Too many redirects");
    }

    /** 使用 HttpURLConnection 发送标准 HTTP 方法（OPTIONS、GET 等） */
    private HttpURLConnection openHttpUrlConnection(String urlStr, String method, boolean withAuth) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // 支持 HTTPS 自签名证书
        if (conn instanceof HttpsURLConnection) {
            HttpsURLConnection httpsConn = (HttpsURLConnection) conn;
            try {
                SSLContext sslContext = createLenientSslContext();
                httpsConn.setSSLSocketFactory(sslContext.getSocketFactory());
                httpsConn.setHostnameVerifier(LENIENT_HOSTNAME_VERIFIER);
                Log.d(TAG, "SSL: trust-all configured for " + urlStr);
            } catch (Exception e) {
                Log.w(TAG, "SSL: failed to set trust-all, using default: " + e);
            }
        }

        conn.setRequestMethod(method);
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);
        conn.setRequestProperty("User-Agent", "AndroidMusicPlayer/1.0");
        if (withAuth && authHeader != null) {
            conn.setRequestProperty("Authorization", authHeader);
        }
        return conn;
    }

    // ---- Raw HTTP engine (for PROPFIND, not supported by HttpURLConnection) ----

    private static class RawHttpResponse {
        final int statusCode;
        final byte[] body;
        RawHttpResponse(int statusCode, byte[] body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    /** 通过原始 Socket 发送 HTTP 请求（兼容所有方法包括 PROPFIND） */
    private RawHttpResponse executeRawRequest(String urlStr, String method, String authHeader, byte[] reqBody) throws IOException {
        URL url = new URL(urlStr);
        boolean isHttps = "https".equals(url.getProtocol());
        int port = url.getPort();
        if (port <= 0) port = isHttps ? 443 : 80;
        String host = url.getHost();
        String path = url.getFile();
        if (path == null || path.isEmpty()) path = "/";

        Socket socket = createSocket(host, port, isHttps);
        try {
            socket.setSoTimeout(TIMEOUT);
            OutputStream out = socket.getOutputStream();

            // 构建原始 HTTP 请求
            StringBuilder header = new StringBuilder();
            header.append(method).append(" ").append(path).append(" HTTP/1.1\r\n");
            header.append("Host: ").append(host).append("\r\n");
            header.append("User-Agent: AndroidMusicPlayer/1.0\r\n");
            if (authHeader != null) {
                header.append("Authorization: ").append(authHeader).append("\r\n");
            }
            header.append("Depth: 1\r\n");
            header.append("Connection: close\r\n");
            if (reqBody != null) {
                header.append("Content-Type: application/xml; charset=utf-8\r\n");
                header.append("Content-Length: ").append(reqBody.length).append("\r\n");
            }
            header.append("\r\n");

            Log.d(TAG, method + " -> " + host + ":" + port + path);
            out.write(header.toString().getBytes("UTF-8"));
            if (reqBody != null) {
                out.write(reqBody);
            }
            out.flush();

            // 读取 response
            InputStream in = socket.getInputStream();

            // 解析状态行: "HTTP/1.1 207 Multi-Status"
            String statusLine = readLine(in);
            if (statusLine == null || statusLine.length() < 12) {
                throw new IOException("Invalid HTTP response: " + statusLine);
            }
            int statusCode;
            try {
                statusCode = Integer.parseInt(statusLine.substring(9, 12).trim());
            } catch (Exception e) {
                throw new IOException("Cannot parse status code from: " + statusLine);
            }

            // 解析响应头
            int contentLength = -1;
            boolean isChunked = false;
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                String lower = line.toLowerCase();
                if (lower.startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    } catch (NumberFormatException ignored) {}
                } else if (lower.startsWith("transfer-encoding:")) {
                    isChunked = line.substring(18).trim().equalsIgnoreCase("chunked");
                }
            }

            // 读取响应体
            byte[] body;
            if (isChunked) {
                body = readChunkedBody(in);
            } else if (contentLength >= 0) {
                body = readExactBytes(in, contentLength);
            } else {
                body = readRemainingBytes(in);
            }

            Log.d(TAG, method + " response: " + statusCode + " (" + (body != null ? body.length : 0) + " bytes)");
            return new RawHttpResponse(statusCode, body);

        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private Socket createSocket(String host, int port, boolean isHttps) throws IOException {
        if (isHttps) {
            try {
                SSLContext sslContext = createLenientSslContext();
                SSLSocketFactory factory = sslContext.getSocketFactory();
                SSLSocket sslSocket = (SSLSocket) factory.createSocket(host, port);
                sslSocket.startHandshake();
                return sslSocket;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("SSL connection failed: " + e.getMessage(), e);
            }
        } else {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), TIMEOUT);
            return socket;
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                in.read(); // skip \n
                break;
            }
            if (b == '\n') break;
            sb.append((char) b);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static byte[] readExactBytes(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int n = in.read(buf, offset, length - offset);
            if (n < 0) break;
            offset += n;
        }
        return buf;
    }

    private static byte[] readChunkedBody(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (true) {
            String chunkSizeLine = readLine(in);
            if (chunkSizeLine == null) break;
            int chunkSize;
            try {
                chunkSize = Integer.parseInt(chunkSizeLine.trim(), 16);
            } catch (NumberFormatException e) {
                break;
            }
            if (chunkSize == 0) break;
            byte[] chunk = readExactBytes(in, chunkSize);
            buf.write(chunk);
            readLine(in); // CRLF after chunk
        }
        return buf.toByteArray();
    }

    private static byte[] readRemainingBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = in.read(tmp)) != -1) {
            buf.write(tmp, 0, n);
        }
        return buf.toByteArray();
    }

    private static final HostnameVerifier LENIENT_HOSTNAME_VERIFIER = (hostname, session) -> true;

    private static SSLContext createLenientSslContext() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());
        return sslContext;
    }

    /** 获取信任所有证书的 SSLSocketFactory，用于 ExoPlayer 播放 HTTPS 音频 */
    public static javax.net.ssl.SSLSocketFactory getTrustAllSslSocketFactory() {
        try {
            return createLenientSslContext().getSocketFactory();
        } catch (Exception e) {
            return null;
        }
    }

    /** 获取信任所有主机名的 HostnameVerifier，用于 ExoPlayer 播放 HTTPS 音频 */
    public static HostnameVerifier getTrustAllHostnameVerifier() {
        return LENIENT_HOSTNAME_VERIFIER;
    }

    private List<CloudFile> parsePropfindResponse(String xml, String parentPath) throws XmlPullParserException, IOException {
        List<CloudFile> files = new ArrayList<>();
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(new StringReader(xml));

        CloudFile current = null;
        String currentTag = "";
        boolean inProp = false;
        boolean inResourceType = false;
        boolean inCollection = false;

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            String tag = parser.getName();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    currentTag = tag;
                    if ("response".equalsIgnoreCase(tag)) {
                        current = new CloudFile();
                        inProp = false;
                        inResourceType = false;
                        inCollection = false;
                    } else if ("prop".equalsIgnoreCase(tag)) {
                        inProp = true;
                    } else if ("resourcetype".equalsIgnoreCase(tag)) {
                        inResourceType = true;
                    } else if ("collection".equalsIgnoreCase(tag)) {
                        inCollection = true;
                        if (current != null) current.isDirectory = true;
                    }
                    break;

                case XmlPullParser.TEXT:
                    if (current == null) break;
                    String text = parser.getText();
                    if (text == null) break;

                    if ("href".equalsIgnoreCase(currentTag) && !inProp) {
                        current.href = text.trim();
                        String decoded = current.href;
                        try {
                            decoded = java.net.URLDecoder.decode(current.href, "UTF-8");
                        } catch (Exception ignored) {}
                        current.href = decoded;
                    } else if (inProp) {
                        if ("displayname".equalsIgnoreCase(currentTag)) {
                            current.name = text.trim();
                        } else if ("getcontentlength".equalsIgnoreCase(currentTag)) {
                            try {
                                current.size = Long.parseLong(text.trim());
                            } catch (NumberFormatException ignored) {}
                        } else if ("getlastmodified".equalsIgnoreCase(currentTag)) {
                            current.modifiedDate = text.trim();
                        }
                    }
                    break;

                case XmlPullParser.END_TAG:
                    if ("response".equalsIgnoreCase(tag)) {
                        if (current != null) {
                            if (current.name == null || current.name.isEmpty()) {
                                String href = current.href != null ? current.href : "";
                                String name = href;
                                if (name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1);
                                if (name.isEmpty()) name = href;
                                current.name = name;
                            }
                            if (!current.href.equals(parentPath.isEmpty() ? "/" : parentPath + "/")
                                    && !current.href.equals(parentPath)) {
                                files.add(current);
                            }
                        }
                        current = null;
                        inProp = false;
                        inResourceType = false;
                        inCollection = false;
                    } else if ("prop".equalsIgnoreCase(tag)) {
                        inProp = false;
                    } else if ("resourcetype".equalsIgnoreCase(tag)) {
                        inResourceType = false;
                    }
                    currentTag = "";
                    break;
            }
            eventType = parser.next();
        }
        return files;
    }
}
