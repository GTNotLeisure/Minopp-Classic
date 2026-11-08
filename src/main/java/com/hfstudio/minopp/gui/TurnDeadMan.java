package com.hfstudio.minopp.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.hfstudio.minopp.MinoClient;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.item.ItemHandCards;

public class TurnDeadMan {

    public static double deadManElapsedTicks;

    private static final double ALARM_DELAY = 8 * 20;

    public static void pedal() {
        deadManElapsedTicks = Math.min(0, deadManElapsedTicks);
    }

    public static void tick(CardGame game, float partialTicks) {
        deadManElapsedTicks += partialTicks;
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        CardPlayer cardPlayer = ItemHandCards.getCardPlayer(player);
        CardPlayer currentPlayer = game.players.get(game.currentPlayerIndex);
        boolean myTurn = cardPlayer.equals(currentPlayer);
        if (!myTurn) deadManElapsedTicks = 0;
    }

    public static void setOutsideGame() {
        MinoClient.globalFovModifier = 1;
        deadManElapsedTicks = 0;
    }

    public static boolean isAlarmActive() {
        return deadManElapsedTicks > ALARM_DELAY;
    }
}
