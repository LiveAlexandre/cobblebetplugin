package me.cobbleBet.commands;

import me.cobbleBet.Main;
import me.cobbleBet.economy.EconomyManager;
import me.cobbleBet.players.PlayerWallet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WalletCommand implements CommandExecutor, TabCompleter {

    private final MiniMessage mm = MiniMessage.miniMessage();

    private EconomyManager eco() {
        return Main.getInstance().getEconomyManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            sendBalance(player);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "help" -> sendHelp(player);
            case "balance", "bal" -> sendBalance(player);
            case "withdraw" -> handleWithdraw(player, args);
            case "deposit" -> handleDeposit(player, args);
            case "set" -> handleSet(player, args);

            default -> player.sendMessage(error("Unknown subcommand. Use /wallet help"));
        }

        return true;
    }

    // =========================
    // BALANCE
    // =========================
    private void sendBalance(Player player) {

        PlayerWallet wallet = Main.getWallet(player.getUniqueId());

        StringBuilder sb = new StringBuilder();
        sb.append("<gradient:#00ffcc:#0066ff><bold>Wallet</bold></gradient>\n");

        // =========================
        // ITEM BALANCES
        // =========================
        for (Map.Entry<Material, Double> entry : wallet.getItemBalances().entrySet()) {

            Material mat = entry.getKey();
            double amount = entry.getValue();

            if (amount <= 0) continue;

            boolean isActive =
                    Main.economyType.equalsIgnoreCase("item")
                            && mat == Main.economyItem;

            if (isActive) {
                sb.append("<yellow>")
                        .append(mat.name())
                        .append(": ")
                        .append(amount)
                        .append("</yellow>\n");
            } else {
                sb.append("<gray>")
                        .append(mat.name())
                        .append(": ")
                        .append(amount)
                        .append("</gray>\n");
            }
        }

        // =========================
        // VAULT BALANCE
        // =========================
        double vault = wallet.getVaultBalance();

        if (Main.economyType.equalsIgnoreCase("vault")) {
            sb.append("<yellow>" + Main.vaultCurrencyName + ": $")
                    .append(vault)
                    .append("</yellow>\n");
        } else {
            sb.append("<gray>" + Main.vaultCurrencyName + ": $")
                    .append(vault)
                    .append("</gray>\n");
        }

        player.sendMessage(mm.deserialize(sb.toString()));
    }

    // =========================
    // WITHDRAW
    // =========================
    private void handleWithdraw(Player player, String[] args) {

        if (args.length < 2) {
            player.sendMessage(error("Usage: /wallet withdraw <amount>"));
            return;
        }

        double amount = parseAmount(player, args[1]);
        if (amount <= 0) return;

        if (!eco().withdraw(player, amount)) {

            player.sendMessage(error(
                    Main.economyType.equalsIgnoreCase("item")
                            ? "Not enough items or inventory issue."
                            : "Not enough balance."
            ));
            return;
        }

        player.sendMessage(success("Withdrawn " + amount));
    }

    // =========================
    // DEPOSIT
    // =========================
    private void handleDeposit(Player player, String[] args) {

        if (args.length < 2) {
            player.sendMessage(error("Usage: /wallet deposit <amount>"));
            return;
        }

        double amount = parseAmount(player, args[1]);
        if (amount <= 0) return;
        if(eco().getBalance(player) + amount > Main.maximumBalance) {
            player.sendMessage(error(
                    "The server's max Wallet balance is: " + amount
            ));
            return;
        }

        if (!eco().deposit(player, amount)) {

            player.sendMessage(error(
                    Main.economyType.equalsIgnoreCase("item")
                            ? "You don't have enough items."
                            : "Deposit failed."
            ));
            return;
        }

        player.sendMessage(success("Deposited " + amount));
    }

    // =========================
    // SET (ADMIN)
    // =========================
    private void handleSet(Player player, String[] args) {

        if (!player.hasPermission("cobblebet.wallet.admin")) {
            player.sendMessage(error("No permission."));
            return;
        }

        if (args.length < 3) {
            player.sendMessage(error("Usage: /wallet set <player> <amount>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);

        if (target == null) {
            player.sendMessage(error("Player not found."));
            return;
        }

        double amount = parseAmount(player, args[2]);
        if (amount < 0) return;

        eco().setBalance(target, amount);

        player.sendMessage(success("Set " + target.getName() + "'s balance to " + amount));
        target.sendMessage(success("Your balance has been set to " + amount));
    }

    // =========================
    // HELP
    // =========================
    private void sendHelp(Player player) {

        player.sendMessage(mm.deserialize(
                "<gradient:#00ffcc:#0066ff><bold>Wallet Help</bold></gradient>\n" +
                        "<gray>/wallet</gray> View balance\n" +
                        "<gray>/wallet deposit <amount></gray>\n" +
                        "<gray>/wallet withdraw <amount></gray>\n" +
                        "<gray>/wallet set <player> <amount></gray> <red>(Admin)</red>"
        ));
    }

    // =========================
    // UTILS
    // =========================
    private double parseAmount(Player player, String input) {

        try {
            double amount = Double.parseDouble(input);

            if (amount <= 0) {
                player.sendMessage(error("Amount must be positive."));
                return -1;
            }

            return amount;

        } catch (NumberFormatException e) {
            player.sendMessage(error("Invalid number."));
            return -1;
        }
    }

    private Component error(String msg) {
        return mm.deserialize(
                "<gradient:#ff4d4d:#cc0000><bold>Error</bold></gradient> <gray>" + msg
        );
    }

    private Component success(String msg) {
        return mm.deserialize(
                "<gradient:#00ff88:#00ccff><bold>Success</bold></gradient> <gray>" + msg
        );
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 1) {
            return List.of("balance", "deposit", "withdraw", "set", "help");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {

            List<String> names = new ArrayList<>();

            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }

            return names;
        }

        return List.of();
    }
}