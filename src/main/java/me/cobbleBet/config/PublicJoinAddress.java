package me.cobbleBet.config;

import me.cobbleBet.Main;
import org.bukkit.Bukkit;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PublicJoinAddress {
    private static final Pattern HOST = Pattern.compile("^([A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?)(?::(\\d{1,5}))?$");
    private static final Pattern IPV6 = Pattern.compile("^\\[([0-9A-Fa-f:]+)](?::(\\d{1,5}))?$");
    private static final Set<String> WILDCARDS = Set.of("0.0.0.0", "::", "[::]", "127.0.0.1", "localhost");

    private PublicJoinAddress() {}

    public static Detection resolve(Main plugin) {
        String configured = normalize(plugin.getConfig().getString("publicAddress", ""));
        if (configured != null && !configured.isBlank()) return new Detection(configured, "configured");

        String allocationIp = firstUsableHost("SERVER_IP", "P_SERVER_IP", "PUBLIC_IP", "EXTERNAL_IP");
        int allocationPort = firstValidPort("SERVER_PORT", "P_SERVER_PORT", "PUBLIC_PORT", "EXTERNAL_PORT");
        if (allocationPort < 1) allocationPort = Bukkit.getPort();
        if (!allocationIp.isBlank() && allocationPort > 0) {
            return new Detection(withPort(allocationIp, allocationPort), "host-allocation");
        }

        String binding = usableHost(Bukkit.getIp());
        if (!binding.isBlank() && Bukkit.getPort() > 0) {
            return new Detection(withPort(binding, Bukkit.getPort()), "server-binding");
        }
        return new Detection("", "unavailable");
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String address = value.trim();
        if (address.isBlank()) return "";
        if (address.length() > 255 || address.matches(".*[\\s/?#@].*") || address.contains("://")) return null;
        Matcher matcher = IPV6.matcher(address);
        if (!matcher.matches()) matcher = HOST.matcher(address);
        if (!matcher.matches()) return null;
        if (matcher.group(2) != null) {
            int port = validPort(matcher.group(2));
            if (port < 1) return null;
        }
        return address;
    }

    private static String usableHost(String value) {
        if (value == null) return "";
        String host = value.trim();
        if (host.isBlank() || WILDCARDS.contains(host.toLowerCase(Locale.ROOT))) return "";
        if (host.contains(":")) {
            String bracketed = host.startsWith("[") && host.endsWith("]") ? host : "[" + host + "]";
            return IPV6.matcher(bracketed).matches() ? bracketed : "";
        }
        if (!HOST.matcher(host).matches()) return "";
        if (host.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")) return isPublicIpv4(host) ? host : "";
        return host;
    }

    private static String firstUsableHost(String... names) {
        for (String name : names) {
            String host = usableHost(System.getenv(name));
            if (!host.isBlank()) return host;
        }
        return "";
    }

    private static int firstValidPort(String... names) {
        for (String name : names) {
            int port = validPort(System.getenv(name));
            if (port > 0) return port;
        }
        return -1;
    }

    private static boolean isPublicIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) return false;
        int[] octets = new int[4];
        try {
            for (int i = 0; i < 4; i++) {
                octets[i] = Integer.parseInt(parts[i]);
                if (octets[i] < 0 || octets[i] > 255) return false;
            }
        } catch (NumberFormatException ignored) { return false; }
        return octets[0] != 0 && octets[0] != 10 && octets[0] != 127
                && !(octets[0] == 169 && octets[1] == 254)
                && !(octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                && !(octets[0] == 192 && octets[1] == 168)
                && !(octets[0] == 100 && octets[1] >= 64 && octets[1] <= 127)
                && octets[0] < 224;
    }

    private static int validPort(String value) {
        try {
            int port = Integer.parseInt(value == null ? "" : value.trim());
            return port >= 1 && port <= 65535 ? port : -1;
        } catch (NumberFormatException ignored) { return -1; }
    }

    private static String withPort(String host, int port) {
        return port == 25565 ? host : host + ":" + port;
    }

    public record Detection(String address, String source) {}
}
