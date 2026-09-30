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

        if (args.length >= 2 && args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cThis menu is available in-game.");
                return true;
            }
            String action = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            boolean walletAction = Main.getInstance().menuController.isWalletDialogAction(action);
            if (!walletAction && Main.isPermissionRequired("admin") && !sender.hasPermission("cobblebet.admin")) {
                sender.sendMessage("§cYou don't have permission to use the owner menus.");
                return true;
            }
            Main.getInstance().menuController.handleDialogAction(player, action);
            return true;
        }

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

        if (args[0].equalsIgnoreCase("game") || args[0].equalsIgnoreCase("games")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("§cPhysical games must be managed in-game."); return true; }
            if (args.length == 1) { Main.getInstance().menuController.openPhysicalGames(player); return true; }
            if (!args[1].equalsIgnoreCase("coinflip")) { sender.sendMessage("§eUsage: /cobblebet game coinflip"); return true; }
            if (args.length == 2) { Main.getInstance().menuController.openCoinflipBoards(player); return true; }
            var boards = Main.getInstance().coinflipBoardManager;
            if (args[2].equalsIgnoreCase("create")) {
                String style=args.length>=4?args[3]:"street";
                String name=args.length>=5?String.join(" ",java.util.Arrays.copyOfRange(args,4,args.length)):"COINFLIP";
                boards.create(player,style,name);return true;
            }
            String id=args[2];
            if(args.length<4){Main.getInstance().menuController.openCoinflipBoard(player,id);return true;}
            switch(args[3].toLowerCase()){
                case "movehere"->boards.moveHere(player,id);
                case "remove"->boards.remove(player,id);
                case "style"->{if(args.length<5)player.sendMessage("§eUsage: /cobblebet game coinflip "+id+" style <style>");else boards.changeStyle(player,id,args[4]);}
                case "rename"->{if(args.length<5)player.sendMessage("§eUsage: /cobblebet game coinflip "+id+" rename <name>");else boards.rename(player,id,String.join(" ",java.util.Arrays.copyOfRange(args,4,args.length)));}
                default->player.sendMessage("§eActions: movehere, style <style>, rename <name>, remove");
            }
            return true;
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

        sender.sendMessage("§cUnknown subcommand. Use: game, reload, panel, or key <approval-key>.");
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
            completions.add("game");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("game") || args[0].equalsIgnoreCase("games"))) completions.add("coinflip");
        if (args.length == 3 && args[1].equalsIgnoreCase("coinflip")) { completions.add("create"); completions.addAll(Main.getInstance().coinflipBoardManager.summaries().stream().map(me.cobbleBet.visuals.CoinflipBoardManager.BoardSummary::id).toList()); }
        if (args.length == 4 && args[1].equalsIgnoreCase("coinflip") && args[2].equalsIgnoreCase("create")) completions.addAll(Main.getInstance().coinflipBoardManager.styles());
        if (args.length == 4 && args[1].equalsIgnoreCase("coinflip") && !args[2].equalsIgnoreCase("create")) completions.addAll(List.of("movehere","style","rename","remove"));
        if (args.length == 5 && args[1].equalsIgnoreCase("coinflip") && args[3].equalsIgnoreCase("style")) completions.addAll(Main.getInstance().coinflipBoardManager.styles());

        return completions;
    }
}
