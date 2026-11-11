package com.hfstudio.minopp.gui;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;

import org.lwjgl.input.Keyboard;

import com.hfstudio.minopp.block.BlockMinoTable;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.PendingDecision;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.network.C2SPlayCardPacket;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GameControls {

    public enum Action {

        PLAY("play", Keyboard.KEY_SPACE),
        SHOUT("shout", Keyboard.KEY_U),
        DOUBT("doubt", Keyboard.KEY_C),
        DRAW("draw", Keyboard.KEY_LCONTROL),
        PREVIOUS("previous", Keyboard.KEY_LEFT),
        NEXT("next", Keyboard.KEY_RIGHT),
        CONFIRM("confirm", Keyboard.KEY_RETURN),
        STACK("stack", Keyboard.KEY_S);

        public final ActionKeyBinding binding;

        Action(String id, int key) {
            binding = new ActionKeyBinding("key.minopp." + id, key, "key.categories.minopp");
        }
    }

    private static final List<Action> ACTIONS = List.of(Action.values());

    private GameControls() {}

    public static void register() {
        for (Action action : ACTIONS) ClientRegistry.registerKeyBinding(action.binding);
    }

    public static Action actionFor(int keyCode) {
        if (keyCode == Keyboard.KEY_NONE) return null;
        for (Action action : ACTIONS) if (action.binding.getKeyCode() == keyCode) return action;
        if (keyCode == Keyboard.KEY_NUMPADENTER && Action.CONFIRM.binding.getKeyCode() == Keyboard.KEY_RETURN)
            return Action.CONFIRM;
        return null;
    }

    public static String display(Action action) {
        return GameSettings.getKeyDisplayString(action.binding.getKeyCode());
    }

    /**
     * Dispatches one action through the existing hand flow; searching and decision screens control their own scope.
     */
    public static boolean handle(int keyCode) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null && !(mc.currentScreen instanceof HandScreen)) return false;
        return dispatch(keyCode, actionFor(keyCode));
    }

    private static boolean dispatch(int keyCode, Action action) {
        if (action == null) return false;
        Minecraft mc = Minecraft.getMinecraft();
        HandScreen.Context context = HandScreen.Context.resolve(mc);
        if (context == null) return false;
        if (keyCode > 0 && Keyboard.isRepeatEvent() && action != Action.PREVIOUS && action != Action.NEXT) return true;
        int index = ItemHandCards.getClientHandIndex(mc.thePlayer);
        switch (action) {
            case PLAY -> HandScreen.play(context, index);
            case SHOUT -> HandScreen.shout(context);
            case DOUBT -> doubt(context);
            case DRAW -> HandScreen.draw(context);
            case PREVIOUS -> HandScreen.select(context, index - 1);
            case NEXT -> HandScreen.select(context, index + 1);
            case CONFIRM -> {
                if (!(mc.currentScreen instanceof HandScreen)) return false;
                HandScreen.play(context, index);
            }
            case STACK -> {
                if (context.game.pendingDraw <= 0) return false;
                HandScreen.play(context, index);
            }
        }
        return true;
    }

    public static boolean handleUtility(int keyCode) {
        Action action = actionFor(keyCode);
        return (action == Action.SHOUT || action == Action.DOUBT) && dispatch(keyCode, action);
    }

    private static boolean canDoubt(HandScreen.Context context, CardPlayer target) {
        return target != null && !target.eliminated
            && !target.equals(context.me)
            && !target.equals(context.game.currentPlayer())
            && !target.hasShoutedMino
            && target.hand.size() == 1;
    }

    private static void doubt(HandScreen.Context context) {
        PendingDecision decision = context.game.decisionFor(context.me.uuid);
        if (decision != null && decision.type == PendingDecision.Type.CHALLENGE) {
            if (!decision.isAnswered() && System.currentTimeMillis() < decision.deadline)
                C2SPlayCardPacket.Client.sendDecisionC2S(context.pos, decision, 1, null);
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        MovingObjectPosition hit = mc.objectMouseOver;
        Entity aimed = hit == null ? null : hit.entityHit;
        CardPlayer target = aimed == null ? null : context.game.deAmputate(aimed.getUniqueID());
        if (!canDoubt(context, target)) target = context.game.deAmputate(context.table.lastPlayedByUuid);
        if (!canDoubt(context, target)) {
            target = null;
            for (CardPlayer candidate : context.game.players) if (canDoubt(context, candidate)) {
                if (target != null) {
                    notice("gui.minopp.controls.doubt_target", display(Action.DOUBT));
                    return;
                }
                target = candidate;
            }
        }
        if (target == null) {
            notice("gui.minopp.controls.no_doubt_target");
            return;
        }
        UUID targetId = target.uuid;
        C2SPlayCardPacket.Client.sendDoubtMinoC2S(context.pos, context.me, targetId);
    }

    private static void notice(String key, Object... arguments) {
        String text = Chat.translatable(key, arguments)
            .getFormattedText();
        HandScreen.notice(text);
        BlockMinoTable.Client.displayActionBar(text);
    }
}
