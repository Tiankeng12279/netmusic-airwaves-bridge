package com.netmusicairwaves.audio;

import com.netmusicairwaves.Config;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class UrlPolicy {
    private UrlPolicy() {
    }

    public static URI validate(URI uri) throws IOException {
        if (uri == null || !uri.isAbsolute() || uri.getHost() == null) {
            throw new IOException("Audio URL has no valid host");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        boolean http = scheme.equals("http");
        boolean https = scheme.equals("https");
        if (!https && !(http && Config.ALLOW_HTTP.get())) {
            throw new IOException("Unsupported URL scheme: " + scheme);
        }
        if (uri.getUserInfo() != null) {
            throw new IOException("Audio URLs with user info are not allowed");
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            throw new IOException("Only ports 80 and 443 are allowed");
        }

        String host = normalizeHost(uri.getHost());
        if (!hostAllowed(host)) {
            throw new IOException("Audio host is not allowed: " + host);
        }
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (isBlockedAddress(address)) {
                throw new IOException("Audio host resolves to a non-public address: " + host);
            }
        }
        return uri;
    }

    private static String normalizeHost(String host) {
        String normalized = Objects.requireNonNull(host).toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean hostAllowed(String host) {
        List<? extends String> allowed = Config.ALLOWED_HOSTS.get();
        if (allowed.isEmpty()) {
            return false;
        }
        for (String rawPattern : allowed) {
            String pattern = rawPattern == null ? "" : rawPattern.trim().toLowerCase(Locale.ROOT);
            if (pattern.isEmpty()) {
                continue;
            }
            if (pattern.equals("*")) {
                return true;
            }
            if (pattern.startsWith("*.")) {
                String suffix = pattern.substring(2);
                if (host.length() > suffix.length() && host.endsWith(suffix)) {
                    return true;
                }
            } else if (host.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            return first == 0
                    || first == 10
                    || first == 127
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)
                    || (first == 192 && second == 0 && (bytes[2] & 0xFF) == 0)
                    || (first == 198 && (second == 18 || second == 19))
                    || first >= 224;
        }

        if (bytes.length == 16) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            if ((first & 0xFE) == 0xFC || (first == 0xFE && (second & 0xC0) == 0x80) || first == 0xFF) {
                return true;
            }
            boolean ipv4Mapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) {
                    ipv4Mapped = false;
                    break;
                }
            }
            if (ipv4Mapped && (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF) {
                byte[] ipv4 = new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
                try {
                    return isBlockedAddress(InetAddress.getByAddress(ipv4));
                } catch (Exception ignored) {
                    return true;
                }
            }
        }
        return false;
    }
}
