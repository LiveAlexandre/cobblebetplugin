package me.cobbleBet.commands;

import me.cobbleBet.Main;
import me.cobbleBet.economy.EconomyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

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

        double balance = eco().getBalance(player);
        String currency = "$";
        if(Main.economyType.equalsIgnoreCase("item"))
            currency = Main.economyItem.name();

        player.sendMessage(mm.deserialize(
                "<gradient:#00ffcc:#0066ff><bold>Wallet</bold></gradient>\n" +
                        "<gray>Balance:</gray> <white>" + balance + " " + currency
        ));
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

            if (Main.economyType.equalsIgnoreCase("item")) {
                player.sendMessage(error("Not enough balance or inventory is full."));
            } else {
                player.sendMessage(error("Not enough balance."));
            }

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

        if (!eco().deposit(player, amount)) {

            if (Main.economyType.equalsIgnoreCase("item")) {
                player.sendMessage(error(
                        "You don't have enough " +
                                Main.economyItem.name().toLowerCase()
                ));
            } else {
                player.sendMessage(error("Failed to deposit money."));
            }

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
                        "<gray>/wallet</gray> <white>View your balance</white>\n" +
                        "<gray>/wallet balance</gray>\n" +
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

            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }

            return names;
        }

        return List.of();
    }
}