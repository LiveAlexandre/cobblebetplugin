package me.cobbleBet.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.cobbleBet.Main;
import me.cobbleBet.storage.PluginDataStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;

public final class PluginUpdateManager {
    private static final String PRODUCTION_DOWNLOAD_URL = "https://cobblebet.com/api/download-plugin";
    private static final String PRODUCTION_RELEASE_URL = "https://cobblebet.com/api/plugin-release";
    private static final long MAX_PLUGIN_SIZE = 50_000_000L;

    private final Main plugin;
    private final PluginDataStore dataStore;
    private final ConcurrentHashMap<UUID, String> notifiedVersions = new ConcurrentHashMap<>();
    private final AtomicBoolean downloadRunning = new AtomicBoolean(false);
    private final AtomicBoolean releaseCheckRunning = new AtomicBoolean(false);

    private volatile String officialVersion = "";
    private volatile String officialJarName = "CobbleBet.jar";
    private volatile String officialSha256 = "";
    private volatile boolean releaseAllowsAutoUpdate;
    private volatile String stagedVersion = "";
    private volatile String downloadFailure = "";
    private volatile String installedNoticeVersion = "";

    public PluginUpdateManager(Main plugin, PluginDataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
    }

    public void start() {
        prepareInstalledUpdateNotice();
        refreshOfficialRelease();
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOfficialRelease,
                20L * 60L * 10L, 20L * 60L * 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            notifiedVersions.entrySet().removeIf(entry -> entry.getValue().startsWith("needed:"));
            Bukkit.getOnlinePlayers().forEach(this::notifyAdmin);
        }, 20L * 180L, 20L * 60L * 30L);
    }

    public void setOfficialRelease(String version, String jarName, String sha256, boolean autoUpdateAllowed) {
        if (version != null && !version.isBlank()) officialVersion = version.trim();
        if (jarName != null && !jarName.isBlank()) officialJarName = jarName.trim();
        officialSha256 = sha256 != null && sha256.matches("[a-fA-F0-9]{64}")
                ? sha256.toLowerCase(Locale.ROOT) : "";
        releaseAllowsAutoUpdate = autoUpdateAllowed;
        if (!autoUpdateAllowed && officialVersion.equals(stagedVersion)) {
            Bukkit.getScheduler().runTask(plugin, this::cancelStagedUpdate);
        }
        checkForUpdate();
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::notifyAdmin));
    }

    public String officialVersion() {
        return officialVersion;
    }

    public String status() {
        String installedVersion = plugin.getDescription().getVersion();
        if (officialVersion.isBlank()) return "Waiting for release information";
        if (!isOlderVersion(installedVersion, officialVersion)) return "Up to date";
        if (officialVersion.equals(stagedVersion)) return "Downloaded " + officialVersion + " · restart to install";
        if (!Main.autoUpdateEnabled) return "Update " + officialVersion + " available · automatic updates disabled";
        if (!releaseAllowsAutoUpdate) return "Update " + officialVersion + " requires manual installation";
        if (downloadRunning.get()) return "Downloading " + officialVersion + "…";
        if (!downloadFailure.isBlank()) return "Download failed: " + downloadFailure;
        return "Update " + officialVersion + " available";
    }

    public void requestCheck() {
        refreshOfficialRelease();
        if (officialVersion.isBlank()) plugin.reconnectSocket();
        checkForUpdate();
    }

    public void applyAutoUpdateSetting() {
        if (Main.autoUpdateEnabled) checkForUpdate();
        else cancelStagedUpdate();
    }

    public void checkForUpdate() {
        String installedVersion = plugin.getDescription().getVersion();
        String latestVersion = officialVersion;
        if (!Main.autoUpdateEnabled || !releaseAllowsAutoUpdate || latestVersion.isBlank()
                || !isOlderVersion(installedVersion, latestVersion)) return;
        if (latestVersion.equals(stagedVersion) || !downloadRunning.compareAndSet(false, true)) return;
        downloadFailure = "";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> downloadAndStage(installedVersion, latestVersion));
    }

    private void downloadAndStage(String installedVersion, String latestVersion) {
        File temporary = null;
        try {
            File updateFolder = plugin.getServer().getUpdateFolderFile();
            Files.createDirectories(updateFolder.toPath());
            temporary = File.createTempFile("cobblebet-update-", ".jar", updateFolder);
            HttpClient http = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(20))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl()))
                    .timeout(Duration.ofSeconds(120))
                    .header("Accept", "application/java-archive, application/octet-stream")
                    .header("Accept-Encoding", "identity")
                    .header("Cache-Control", "no-cache, no-transform")
                    .header("User-Agent", "CobbleBet/" + installedVersion)
                    .build();
            downloadWithRetries(http, request, temporary);
            validateDownloadedPlugin(temporary, latestVersion);
            if (!Main.autoUpdateEnabled || !releaseAllowsAutoUpdate || !latestVersion.equals(officialVersion)) {
                plugin.getLogger().info("The CobbleBet update was no longer approved before its download completed; staging was cancelled.");
                return;
            }

            File target = new File(updateFolder, plugin.getPluginFileName());
            moveIntoPlace(temporary, target);
            temporary = null;
            stagedVersion = latestVersion;
            downloadFailure = "";
            plugin.getLogger().info("CobbleBet " + latestVersion + " downloaded to " + target + ". It will install on the next server restart.");
            Bukkit.getScheduler().runTask(plugin, () -> {
                dataStore.set("updater.pendingVersion", latestVersion);
                Bukkit.getOnlinePlayers().forEach(this::notifyAdmin);
            });
        } catch (Exception error) {
            downloadFailure = error.getMessage() == null ? "unknown download error" : error.getMessage();
            plugin.getLogger().warning("Automatic CobbleBet update failed: " + downloadFailure);
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::notifyAdmin));
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary.toPath()); } catch (Exception ignored) {}
            downloadRunning.set(false);
        }
    }

    private void validateDownloadedPlugin(File file, String expectedVersion) throws Exception {
        if (file.length() < 1024 || file.length() > MAX_PLUGIN_SIZE) {
            throw new IllegalStateException("downloaded JAR size is invalid");
        }
        if (!officialSha256.isBlank()) {
            String actualHash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath())));
            if (!actualHash.equalsIgnoreCase(officialSha256)) {
                throw new IllegalStateException("downloaded JAR checksum does not match the published release");
            }
        }
        try (JarFile jar = new JarFile(file)) {
            var descriptorEntry = jar.getJarEntry("plugin.yml");
            if (descriptorEntry == null) throw new IllegalStateException("downloaded file is not a Bukkit plugin JAR");
            String descriptor;
            try (InputStream input = jar.getInputStream(descriptorEntry)) {
                descriptor = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!descriptor.matches("(?ms).*^\\s*name\\s*:\\s*['\"]?CobbleBet['\"]?\\s*$.*")) {
                throw new IllegalStateException("downloaded JAR is not the CobbleBet plugin");
            }
            String version = java.util.regex.Pattern.quote(expectedVersion);
            if (!descriptor.matches("(?ms).*^\\s*version\\s*:\\s*['\"]?" + version + "['\"]?\\s*$.*")) {
                throw new IllegalStateException("downloaded JAR version does not match " + expectedVersion);
            }
        }
    }

    private void downloadWithRetries(HttpClient http, HttpRequest request, File destination) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    response.body().close();
                    throw new IllegalStateException("download returned HTTP " + response.statusCode());
                }
                long announcedSize = response.headers().firstValueAsLong("content-length").orElse(-1L);
                if (announcedSize > MAX_PLUGIN_SIZE) {
                    response.body().close();
                    throw new IllegalStateException("published JAR is larger than 50 MB");
                }
                try (InputStream input = response.body()) {
                    Files.copy(input, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                long downloadedSize = Files.size(destination.toPath());
                if (announcedSize >= 0 && downloadedSize != announcedSize) {
                    throw new IllegalStateException("download ended early (received " + downloadedSize
                            + " of " + announcedSize + " bytes)");
                }
                return;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw error;
            } catch (Exception error) {
                lastError = error;
                Files.deleteIfExists(destination.toPath());
                if (attempt == 3) break;
                plugin.getLogger().warning("CobbleBet update download attempt " + attempt + " failed; retrying: " + error.getMessage());
                Thread.sleep(1_000L * attempt);
            }
        }
        throw lastError == null ? new IllegalStateException("plugin download failed") : lastError;
    }

    private void moveIntoPlace(File source, File target) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                try {
                    Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (Exception error) {
                lastError = error;
                if (attempt < 3) Thread.sleep(250L * attempt);
            }
        }
        throw lastError == null ? new IllegalStateException("could not stage downloaded update") : lastError;
    }

    private void refreshOfficialRelease() {
        if (!releaseCheckRunning.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpClient http = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(15))
                        .build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(releaseInfoUrl()))
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", "application/json")
                        .header("Accept-Encoding", "identity")
                        .header("Cache-Control", "no-cache")
                        .header("User-Agent", "CobbleBet/" + plugin.getDescription().getVersion())
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("release check returned HTTP " + response.statusCode());
                }
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!json.has("success") || !json.get("success").getAsBoolean()) {
                    throw new IllegalStateException("release endpoint did not return a valid release");
                }
                String version = json.has("version") ? json.get("version").getAsString() : "";
                String jarName = json.has("fileName") ? json.get("fileName").getAsString() : "CobbleBet.jar";
                String sha256 = json.has("sha256") ? json.get("sha256").getAsString() : "";
                boolean approved = json.has("autoUpdateApproved") && json.get("autoUpdateApproved").getAsBoolean();
                Bukkit.getScheduler().runTask(plugin,
                        () -> setOfficialRelease(version, jarName, sha256, approved));
            } catch (Exception error) {
                plugin.getLogger().warning("Could not refresh CobbleBet release information: "
                        + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
            } finally {
                releaseCheckRunning.set(false);
            }
        });
    }

    private void cancelStagedUpdate() {
        stagedVersion = "";
        dataStore.set("updater.pendingVersion", null);
        try {
            Files.deleteIfExists(new File(plugin.getServer().getUpdateFolderFile(), plugin.getPluginFileName()).toPath());
        } catch (Exception error) {
            plugin.getLogger().warning("Could not remove the staged CobbleBet update: " + error.getMessage());
        }
    }

    private void prepareInstalledUpdateNotice() {
        String pendingVersion = dataStore.getString("updater.pendingVersion", "").trim();
        if (!pendingVersion.isBlank() && pendingVersion.equals(plugin.getDescription().getVersion())) {
            installedNoticeVersion = pendingVersion;
            dataStore.set("updater.pendingVersion", null);
        }
    }

    public void notifyAdmin(Player player) {
        if (player == null || !player.isOnline()
                || (!player.isOp() && !player.hasPermission("cobblebet.admin"))) return;
        String installedVersion = plugin.getDescription().getVersion();
        if (!installedNoticeVersion.isBlank()) {
            String noticeKey = "installed:" + installedNoticeVersion;
            if (noticeKey.equals(notifiedVersions.put(player.getUniqueId(), noticeKey))) return;
            player.sendMessage(Component.text("[CobbleBet] ", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD)
                    .append(Component.text("Automatically updated to " + installedNoticeVersion + ".", NamedTextColor.GREEN)));
            return;
        }
        if (officialVersion.isBlank() || !isOlderVersion(installedVersion, officialVersion)) return;

        String updateState = officialVersion.equals(stagedVersion) ? "staged"
                : downloadRunning.get() ? "downloading"
                : !downloadFailure.isBlank() ? "failed"
                : !Main.autoUpdateEnabled ? "disabled"
                : !releaseAllowsAutoUpdate ? "manual" : "available";
        String noticeKey = "needed:" + officialVersion + ":" + updateState;
        if (noticeKey.equals(notifiedVersions.put(player.getUniqueId(), noticeKey))) return;

        if (updateState.equals("staged")) {
            player.sendMessage(Component.text("[CobbleBet] ", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD)
                    .append(Component.text("Update " + officialVersion + " is downloaded and will install on the next full server restart.", NamedTextColor.GREEN)));
            return;
        }
        if (updateState.equals("downloading")) {
            player.sendMessage(Component.text("[CobbleBet] ", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD)
                    .append(Component.text("Update " + officialVersion + " is available and is downloading automatically.", NamedTextColor.LIGHT_PURPLE)));
            return;
        }

        Component download = Component.text("Download " + officialJarName, NamedTextColor.LIGHT_PURPLE)
                .decorate(TextDecoration.BOLD, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(downloadUrl()))
                .hoverEvent(HoverEvent.showText(Component.text("Open the official CobbleBet download", NamedTextColor.GRAY)));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("[CobbleBet] ", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD)
                .append(Component.text("A plugin update is available!", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD)));
        player.sendMessage(Component.text("Installed ", NamedTextColor.GRAY)
                .append(Component.text(installedVersion, NamedTextColor.WHITE))
                .append(Component.text("  →  Latest ", NamedTextColor.GRAY))
                .append(Component.text(officialVersion, NamedTextColor.GREEN)));
        player.sendMessage(Component.text("Click to update: ", NamedTextColor.GRAY).append(download));
        String reason = !Main.autoUpdateEnabled
                ? "Automatic updates are disabled. Replace the old JAR and restart your server."
                : !releaseAllowsAutoUpdate
                ? "This release was published for manual installation. Replace the old JAR and restart your server."
                : !downloadFailure.isBlank()
                ? "Automatic update failed (" + downloadFailure + "). Download the JAR manually and restart your server."
                : "Automatic update is starting. Open /cobblebet → Server settings → Updates to see its status.";
        player.sendMessage(Component.text(reason, NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.empty());
    }

    private String downloadUrl() {
        return Main.testMode ? "http://localhost:8908/api/download-plugin" : PRODUCTION_DOWNLOAD_URL;
    }

    private String releaseInfoUrl() {
        return Main.testMode ? "http://localhost:8908/api/plugin-release" : PRODUCTION_RELEASE_URL;
    }

    private static boolean isOlderVersion(String installed, String latest) {
        String[] installedParts = installed.split("[.+-]", 4);
        String[] latestParts = latest.split("[.+-]", 4);
        for (int index = 0; index < 3; index++) {
            int installedPart = parseVersionPart(installedParts, index);
            int latestPart = parseVersionPart(latestParts, index);
            if (installedPart != latestPart) return installedPart < latestPart;
        }
        return installed.contains("-") && !latest.contains("-");
    }

    private static int parseVersionPart(String[] parts, int index) {
        if (index >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[index].replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
