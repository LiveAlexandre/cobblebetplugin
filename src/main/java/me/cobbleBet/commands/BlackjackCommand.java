package me.cobbleBet.commands;

import me.cobbleBet.Main;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public final class BlackjackCommand implements CommandExecutor, TabCompleter {
    @Override public boolean onCommand(@NotNull CommandSender sender,@NotNull Command command,@NotNull String label,@NotNull String[] args){
        if(!(sender instanceof Player player)){sender.sendMessage("§cBlackjack is available in-game only.");return true;}
        if(Main.isPermissionRequired("gamble")&&!player.hasPermission("cobblebet.gamble")){player.sendMessage("§cYou do not have permission to play Blackjack.");return true;}
        var controller=Main.getInstance().blackjackController;
        if(args.length==0){controller.open(player);return true;}
        switch(args[0].toLowerCase()){
            case "bet","start"->{if(args.length<2)controller.openBetMenu(player);else controller.start(player,args[1]);}
            case "hit"->controller.action(player,"hit");case "stand"->controller.action(player,"stand");case "double"->controller.action(player,"double");
            default->player.sendMessage("§eUsage: /blackjack [bet <amount>|hit|stand|double]");
        }return true;
    }
    @Override public List<String> onTabComplete(@NotNull CommandSender sender,@NotNull Command command,@NotNull String alias,@NotNull String[] args){if(args.length==1)return List.of("bet","hit","stand","double");if(args.length==2&&args[0].equalsIgnoreCase("bet"))return List.of("10","100","1000");return List.of();}
}
