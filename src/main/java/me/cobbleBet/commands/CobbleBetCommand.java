package me.cobbleBet.commands;

import me.cobbleBet.Main;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class CobbleBetCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (Main.isPermissionRequired("admin") && !sender.hasPermission("cobblebet.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) Main.getInstance().menuController.openDashboard(player);
            else sender.sendMessage("§eUsage: /cobblebet <stats|settings|panel|reload|key <approval-key>>");
            return true;
        }

        if (args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("health")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("§cThis menu is available in-game."); return true; }
            if (args[0].equalsIgnoreCase("health")) Main.getInstance().menuController.openHealth(player);
            else Main.getInstance().menuController.openStats(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("settings") || args[0].equalsIgnoreCase("gui")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("§cThis menu is available in-game."); return true; }
            Main.getInstance().menuController.openDashboard(player); return true;
        }

        if (args[0].equalsIgnoreCase("panel") || args[0].equalsIgnoreCase("link")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cRun this command in-game so the private panel link can be sent to you.");
                return true;
            }

            if (Main.getInstance().cobbleSocketClient == null || !Main.getInstance().cobbleSocketClient.isApproved()) {
                player.sendMessage("§cCobbleBet is not connected right now. Please try again shortly.");
                return true;
            }

            JsonObject request = new JsonObject();
            request.addProperty("type", "requestAdminPanel");
            request.addProperty("playerUUID", player.getUniqueId().toString());
            request.addProperty("playerName", player.getName());
            Main.getInstance().pendingAdminPanelRequests.put(player.getUniqueId(), System.currentTimeMillis() + 30_000);
            try {
                Main.getInstance().cobbleSocketClient.send(request.toString());
                player.sendMessage(Component.text("Requesting your private CobbleBet admin panel link…", NamedTextColor.GRAY));
            } catch (RuntimeException e) {
                Main.getInstance().pendingAdminPanelRequests.remove(player.getUniqueId());
                player.sendMessage("§cCould not request the admin panel link. Please try again shortly.");
                Main.getInstance().getLogger().warning("Could not send admin panel link request: " + e.getMessage());
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("key") || args[0].equalsIgnoreCase("setkey")) {
            if (args.length != 2) {
                sender.sendMessage("§eUsage: /cobblebet key <approval-key>");
                return true;
            }

            String approvalKey = args[1].trim();
            if (!approvalKey.matches("cb_approved_[a-f0-9]{64}")) {
                sender.sendMessage("§cThat approval key is invalid. Copy the full key issued by CobbleBet and try again.");
                return true;
            }

            String previousKey = Main.cobblebetToken;
            try {
                Main.getInstance().getConfig().set("cobblebetToken", approvalKey);
                Main.getInstance().saveConfig();
                Main.loadConfigValues();
                Main.getInstance().reconnectSocket();
                sender.sendMessage("§aCobbleBet approval key saved. Reconnecting to CobbleBet now.");
            } catch (Exception e) {
                Main.getInstance().getConfig().set("cobblebetToken", previousKey == null ? "" : previousKey);
                sender.sendMessage("§cCould not save the approval key. Check the server console for details.");
                Main.getInstance().getLogger().warning("Could not save the CobbleBet approval key: " + e.getMessage());
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {

            long start = System.currentTimeMillis();

            try {
                Main.getInstance().reloadConfig();
                Main.loadConfigValues();
                me.cobbleBet.storage.WebsiteSettingsStore.load(Main.getInstance());
                Main.getInstance().reconnectSocket();

                long time = System.currentTimeMillis() - start;
                sender.sendMessage("§aCobbleBet config reloaded successfully in §f" + time + "ms§a.");

            } catch (Exception e) {
                sender.sendMessage("§cFailed to reload CobbleBet.");
                e.printStackTrace();
            }

            return true;
        }

        sender.sendMessage("§cUnknown subcommand. Use: reload, panel, or key <approval-key>.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        List<String> completions = new ArrayList<>();

        if (Main.isPermissionRequired("admin") && !sender.hasPermission("cobblebet.admin")) {
            return completions;
        }

        if (args.length == 1) {
            completions.add("reload");
            completions.add("stats"); completions.add("health"); completions.add("settings");
            completions.add("panel");
            completions.add("key");
        }

        return completions;
    }
}
