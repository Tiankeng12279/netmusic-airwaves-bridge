package com.netmusicairwaves.audio;

import com.github.tartaricacid.netmusic.NetMusic;
import com.github.tartaricacid.netmusic.api.NetEaseMusic;
import com.github.tartaricacid.netmusic.api.NetWorker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class SafeAudioDownloader {
    private static final int MAX_REDIRECTS = 6;
    private static final HttpClient CLIENT = createClient();

    private SafeAudioDownloader() {
    }

    private static HttpClient createClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1);
        try {
            builder.proxy(new NetWorker.ConfigProxySelector());
        } catch (Throwable ignored) {
            // Use the system default when NetMusic's optional proxy config is unavailable.
        }
        return builder.build();
    }

    public static byte[] download(URI initialUri, int maxBytes) throws IOException, InterruptedException {
        URI current = UrlPolicy.validate(initialUri);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(45))
                    .header("User-Agent", NetEaseMusic.getUserAgent())
                    .header("Referer", "https://music.163.com/")
                    .header("Origin", "https://music.163.com")
                    .header("Accept", "*/*")
                    .GET();
            addNetEaseHeaders(request, current);

            HttpResponse<InputStream> response = CLIENT.send(request.build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location").orElse(null);
                closeQuietly(response.body());
                if (location == null || location.isBlank()) {
                    throw new IOException("Audio redirect has no Location header");
                }
                current = UrlPolicy.validate(current.resolve(location));
                continue;
            }
            if (status != 200 && status != 206) {
                closeQuietly(response.body());
                throw new IOException("Audio server returned HTTP " + status);
            }

            try (InputStream input = response.body()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[16 * 1024];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedIOException("Audio download cancelled");
                    }
                    total += read;
                    if (total > maxBytes) {
                        throw new IOException("Audio file is larger than the configured limit");
                    }
                    output.write(buffer, 0, read);
                }
                if (total == 0) {
                    throw new IOException("Audio server returned an empty file");
                }
                return output.toByteArray();
            }
        }
        throw new IOException("Too many audio redirects");
    }

    private static void addNetEaseHeaders(HttpRequest.Builder request, URI uri) {
        String host = uri.getHost();
        if (host == null || !(host.equals("music.163.com") || host.endsWith(".163.com"))) {
            return;
        }
        try {
            if (NetMusic.NET_EASE_WEB_API == null) {
                return;
            }
            for (Map.Entry<String, String> entry : NetMusic.NET_EASE_WEB_API.getRequestPropertyData().entrySet()) {
                String name = entry.getKey();
                String value = entry.getValue();
                if (name == null || value == null || name.equalsIgnoreCase("Host")
                        || name.equalsIgnoreCase("Content-Length")
                        || name.equalsIgnoreCase("User-Agent")
                        || name.equalsIgnoreCase("Referer")
                        || name.equalsIgnoreCase("Origin")
                        || name.equalsIgnoreCase("Accept")
                        || name.equalsIgnoreCase("Content-Type")) {
                    continue;
                }
                request.header(name, value);
            }
        } catch (Throwable ignored) {
            // NetMusic headers are an optional compatibility aid.
        }
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }
}
