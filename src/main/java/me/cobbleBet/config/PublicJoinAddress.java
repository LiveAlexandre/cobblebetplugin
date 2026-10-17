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
    private static final String DETECTED_ADDRESS_PATH = "network.detectedJoinAddress";

    private PublicJoinAddress() {}

    public static Detection resolve(Main plugin) {
        String configured = normalize(plugin.getConfig().getString("publicAddress", ""));
        if (configured != null && !configured.isBlank()) return new Detection(configured, "configured");

        String learned = normalize(plugin.getDataStore().getString(DETECTED_ADDRESS_PATH, ""));
        if (learned != null && !learned.isBlank() && isUsableAutomaticAddress(learned)) {
            return new Detection(learned, "player-handshake");
        }

        String completeAllocation = firstUsableAddress(
                "MINECRAFT_SERVER_ADDRESS", "MINECRAFT_ADDRESS", "PUBLIC_ADDRESS",
                "EXTERNAL_ADDRESS", "SERVER_ADDRESS", "ALLOCATED_ADDRESS"
        );
        String allocationIp = firstUsableHost(
                "SERVER_IP", "P_SERVER_IP", "PUBLIC_IP", "EXTERNAL_IP", "ALLOCATED_IP",
                "PRIMARY_IP", "MINECRAFT_SERVER_HOST", "MINECRAFT_HOST", "MC_HOST", "PANEL_IP",
                "MINECRAFT_SERVER_ADDRESS", "MINECRAFT_ADDRESS", "PUBLIC_ADDRESS", "EXTERNAL_ADDRESS",
                "SERVER_ADDRESS", "ALLOCATED_ADDRESS"
        );
        int allocationPort = firstValidPort(
                "SERVER_PORT", "P_SERVER_PORT", "PUBLIC_PORT", "EXTERNAL_PORT", "ALLOCATED_PORT",
                "PRIMARY_PORT", "MINECRAFT_SERVER_PORT", "MINECRAFT_PORT", "MC_PORT", "PANEL_PORT"
        );
        if (allocationPort < 1) allocationPort = Bukkit.getPort();
        if (!completeAllocation.isBlank()) {
            String address = hasExplicitPort(completeAllocation) || allocationPort < 1
                    ? completeAllocation : withPort(completeAllocation, allocationPort);
            return new Detection(address, "host-allocation");
        }
        if (!allocationIp.isBlank() && allocationPort > 0) {
            return new Detection(withPort(allocationIp, allocationPort), "host-allocation");
        }

        String binding = usableHost(Bukkit.getIp());
        if (!binding.isBlank() && Bukkit.getPort() > 0) {
            return new Detection(withPort(binding, Bukkit.getPort()), "server-binding");
        }
        return new Detection("", "unavailable");
    }

    /**
     * Learns the address from a real Minecraft handshake. This is more reliable
     * than the socket's outbound IP for servers behind NAT, a panel, or a proxy.
     */
    public static boolean observePlayerHandshake(Main plugin, String hostname, int port) {
        String configured = normalize(plugin.getConfig().getString("publicAddress", ""));
        if (configured != null && !configured.isBlank()) return false;

        String candidate = fromHandshake(hostname, port);
        if (candidate.isBlank() || candidate.equals(Main.publicJoinAddress)
                && "player-handshake".equals(Main.publicJoinAddressSource)) return false;

        plugin.getDataStore().set(DETECTED_ADDRESS_PATH, candidate);
        Main.publicJoinAddress = candidate;
        Main.publicJoinAddressSource = "player-handshake";
        plugin.getLogger().info("Learned player-facing Minecraft address " + candidate + " from a real player connection.");
        plugin.sendServerStatus();
        return true;
    }

    public static String fromHandshake(String hostname, int port) {
        if (hostname == null) return "";
        String raw = hostname.split("\\u0000", 2)[0].trim();
        while (raw.endsWith(".")) raw = raw.substring(0, raw.length() - 1);
        if (raw.isBlank()) return "";

        String complete = normalize(raw);
        if (complete != null && !complete.isBlank() && hasExplicitPort(complete)
                && isUsableAutomaticAddress(complete)) return complete;

        String host = usableHost(raw);
        if (host.isBlank()) return "";
        int safePort = port >= 1 && port <= 65535 ? port : Bukkit.getPort();
        String candidate = safePort > 0 ? withPort(host, safePort) : host;
        return isUsableAutomaticAddress(candidate) ? candidate : "";
    }

    public static String serverSoftware() {
        String name = Bukkit.getName();
        try {
            Class.forName("io.papermc.paper.configuration.GlobalConfiguration", false,
                    PublicJoinAddress.class.getClassLoader());
            name = name.toLowerCase(Locale.ROOT).contains("purpur") ? "Purpur" : "Paper";
        } catch (ClassNotFoundException ignored) {
            try {
                Class.forName("org.spigotmc.SpigotConfig", false, PublicJoinAddress.class.getClassLoader());
                name = "Spigot";
            } catch (ClassNotFoundException ignoredAgain) {
                if (name == null || name.isBlank()) name = "Bukkit";
            }
        }
        String minecraft = Bukkit.getMinecraftVersion();
        return minecraft == null || minecraft.isBlank() ? name : name + " " + minecraft;
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

    private static String firstUsableAddress(String... names) {
        for (String name : names) {
            String address = normalize(System.getenv(name));
            if (address != null && !address.isBlank() && isUsableAutomaticAddress(address)) return address;
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

    private static boolean hasExplicitPort(String address) {
        Matcher ipv6 = IPV6.matcher(address);
        if (ipv6.matches()) return ipv6.group(2) != null;
        Matcher host = HOST.matcher(address);
        return host.matches() && host.group(2) != null;
    }

    private static boolean isUsableAutomaticAddress(String address) {
        Matcher ipv6 = IPV6.matcher(address);
        Matcher host = HOST.matcher(address);
        String value;
        if (ipv6.matches()) value = ipv6.group(1);
        else if (host.matches()) value = host.group(1);
        else return false;
        return !usableHost(value).isBlank();
    }

    public record Detection(String address, String source) {}
}
