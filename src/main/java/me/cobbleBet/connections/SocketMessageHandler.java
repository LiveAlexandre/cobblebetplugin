package me.cobbleBet.connections;



import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.cobbleBet.Main;
import me.cobbleBet.connections.listeners.*;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.logging.Level;

public class SocketMessageHandler {


    private ArrayList<SocketMessageListener> listeners = new ArrayList<>();
    private final CobbleSocketClient client;

    public SocketMessageHandler(CobbleSocketClient client) {
        this.client = client;
        listeners.add(new SocketTokenResponseListener("tokenResponse", client));
        listeners.add(new SocketAccountLinkCodeListener("accountLinkCode", client));
        listeners.add(new SocketRequestBalanceListener("requestBalance", client));
        listeners.add(new SocketUpdateBalanceListener("updateBalance", client));
        listeners.add(new SocketDepositRequestListener("requestDeposit", client));
        listeners.add(new SocketRequestBroadcastListener("requestBroadcast", client));
        listeners.add(new SocketAdminPanelResponseListener("adminPanelResponse", client));
        listeners.add(new SocketGambleResultListener("gambleResult", client));
        listeners.add(new SocketCoinflipOutcomeListener("coinflipOutcome", client));
        listeners.add(new SocketCoinflipLobbyListener("coinflipLobby", client));
        listeners.add(new SocketCoinflipActionResultListener("coinflipActionResult", client));
        listeners.add(new SocketBlackjackEventListener("blackjackEvent", client));
        listeners.add(new SocketMinesEventListener("minesEvent", client));
        listeners.add(new SocketPlinkoEventListener("plinkoEvent", client));
        listeners.add(new SocketRouletteEventListener("rouletteEvent", client));
        listeners.add(new SocketSetEconomyListener("setEconomy", client));
        listeners.add(new SocketSetPermissionRequirementsListener("setPermissionRequirements", client));
        listeners.add(new SocketSetBroadcastSettingsListener("setBroadcastSettings", client));
        listeners.add(new SocketSetAutoUpdateListener("setAutoUpdate", client));
        listeners.add(new SocketSetPublicJoinAddressListener("setPublicJoinAddress", client));
        listeners.add(new SocketSetWebsiteSettingsListener("setWebsiteSettings", client));
        listeners.add(new SocketWebGamblingStatusListener("webGamblingStatus", client));
        listeners.add(new SocketApprovalAcceptedListener("approvalAccepted", client));
        listeners.add(new SocketPluginReleaseListener("pluginRelease", client));
        listeners.add(new SocketOwnerConsoleListener("ownerConsoleCommand", client));
        listeners.add(new SocketOwnerConsoleListener("ownerConsoleChat", client));
        listeners.add(new SocketOwnerConsoleListener("ownerConsoleComplete", client));
        listeners.add(new SocketOwnerConsoleListener("ownerWalletSet", client));
        listeners.add(new SocketOwnerConsoleListener("ownerConsoleSnapshot", client));
        listeners.add(new SocketApprovalRejectedListener("approvalRejected", client));
    }


    public void handleMessage(String msg) {
        try {
            JsonObject json = JsonParser.parseString(msg).getAsJsonObject();
            if(!json.has("type")) {
                Bukkit.getLogger().log(Level.WARNING, "CobbleBet received a socket message without a type.");
                return;
            }
            String type = json.get("type").getAsString();
            if (type.equalsIgnoreCase("tokenResponse") || type.equalsIgnoreCase("adminPanelResponse")) {
                Main.getInstance().getLogger().info("Received CobbleBet " + type + ".");
            }

            for(SocketMessageListener listener : listeners) {
                if(listener.type.equalsIgnoreCase(type)) {
                    listener.trigger(json);
                    return;
                }
            }
            Bukkit.getLogger().log(Level.WARNING, "CobbleBet received an unsupported socket message type: " + type);
        } catch (RuntimeException error) {
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not process a CobbleBet socket response.", error);
        }
    }

    public ArrayList<SocketMessageListener> getListeners() {
        return listeners;
    }
}
