package me.cobbleBet.commands;

import me.cobbleBet.Main;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public final class MinesCommand implements CommandExecutor,TabCompleter {
    @Override public boolean onCommand(@NotNull CommandSender sender,@NotNull Command command,@NotNull String label,@NotNull String[] args){
        if(!(sender instanceof Player player)){sender.sendMessage("§cMines is available in-game only.");return true;}
        if(Main.isPermissionRequired("gamble")&&!player.hasPermission("cobblebet.gamble")){player.sendMessage("§cYou do not have permission to play Mines.");return true;}
        var controller=Main.getInstance().minesController;
        if(args.length==0){controller.open(player);return true;}
        switch(args[0].toLowerCase()){
            case "bet","start"->{if(args.length<2)controller.openSetup(player);else controller.start(player,args[1],args.length>=3?parse(args[2],3):3);}
            case "tile"->{if(args.length<2)player.sendMessage("§eUsage: /mines tile <1-25>");else controller.tile(player,parse(args[1],0)-1);}
            case "cashout"->controller.cashout(player);
            default->player.sendMessage("§eUsage: /mines [bet <amount> [mines]|tile <1-25>|cashout]");
        }return true;
    }
    private int parse(String value,int fallback){try{return Integer.parseInt(value);}catch(NumberFormatException ignored){return fallback;}}
    @Override public List<String> onTabComplete(@NotNull CommandSender sender,@NotNull Command command,@NotNull String alias,@NotNull String[] args){if(args.length==1)return List.of("bet","tile","cashout");if(args.length==2&&args[0].equalsIgnoreCase("bet"))return List.of("10","100","1000");if(args.length==3&&args[0].equalsIgnoreCase("bet"))return List.of("1","3","5","10","15","20","24");return List.of();}
}
