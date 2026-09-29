package me.cobbleBet.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Loaded reflectively so Paper versions before 1.21.7 can keep using inventory menus. */
public final class NativeDialogMenus {
    private NativeDialogMenus() {}

    public static boolean open(CobbleMenuController controller, Player player, String page) {
        CobbleMenuController.DialogMenu menu = controller.dialogMenu(player, page);
        List<ActionButton> actions = new ArrayList<>();
        for (CobbleMenuController.DialogButton button : menu.buttons()) {
            NamedTextColor color = button.active() ? NamedTextColor.GREEN : NamedTextColor.WHITE;
            actions.add(ActionButton.create(
                Component.text(button.label(), color),
                Component.text(button.tooltip(), NamedTextColor.GRAY),
                180,
                DialogAction.staticAction(ClickEvent.runCommand("/cobblebet menu " + button.action()))
            ));
        }

        ActionButton exit = ActionButton.create(
            Component.text(menu.exitLabel(), NamedTextColor.GRAY),
            Component.text("Close this menu", NamedTextColor.DARK_GRAY),
            180,
            null
        );
        Dialog dialog = Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(Component.text(menu.title(), NamedTextColor.GOLD))
                .canCloseWithEscape(true)
                .body(List.of(DialogBody.plainMessage(Component.text(menu.body(), NamedTextColor.WHITE))))
                .build())
            .type(DialogType.multiAction(actions, exit, actions.size() > 1 ? 2 : 1))
        );
        player.showDialog(dialog);
        return true;
    }
}
