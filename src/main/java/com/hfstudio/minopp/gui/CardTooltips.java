package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardFace;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class CardTooltips {

    private CardTooltips() {}

    public static String title(CardGame game, Card card) {
        return Chat.translatable(
            "gui.minopp.hand.card_points",
            game.face(card)
                .getDisplayName()
                .getFormattedText(),
            CardGame.formatPoints(game.cardPoints(card)))
            .getFormattedText();
    }

    public static String description(CardFace face) {
        String key = "game.minopp.card.type." + face.typeId + ".desc";
        return Chat.translatable(StatCollector.canTranslate(key) ? key : "gui.minopp.hand.card_number")
            .getFormattedText();
    }

    public static List<String> lines(FontRenderer font, CardGame game, Card card, int width) {
        List<String> lines = new ArrayList<>();
        lines.add(title(game, card));
        lines.addAll(
            font.listFormattedStringToWidth(
                EnumChatFormatting.GRAY + description(game.face(card)),
                Math.max(80, Math.min(280, width - 32))));
        return lines;
    }
}
