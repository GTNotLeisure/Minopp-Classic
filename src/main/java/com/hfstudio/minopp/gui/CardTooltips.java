package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardFace;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPack;
import com.hfstudio.minopp.gui.ui.SearchMatch;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;

@SideOnly(Side.CLIENT)
public class CardTooltips {

    private static final Object2BooleanMap<CardFace> SEARCH_MATCHES = new Object2BooleanOpenHashMap<>();
    private static String searchQuery;

    private CardTooltips() {}

    public static boolean matches(CardFace face, String query) {
        if (face.isHidden()) return false;
        if (query == null || query.isEmpty()) return true;
        if (!query.equals(searchQuery)) {
            clearSearchCache();
            searchQuery = query;
        }
        if (SEARCH_MATCHES.containsKey(face)) return SEARCH_MATCHES.getBoolean(face);
        String text = face.getDisplayName()
            .getUnformattedText() + '\n'
            + description(face)
            + '\n'
            + packLine(face);
        boolean match = SearchMatch.matches(text, query);
        SEARCH_MATCHES.put(face, match);
        return match;
    }

    public static void clearSearchCache() {
        SEARCH_MATCHES.clear();
        searchQuery = null;
    }

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

    public static String packLine(CardFace face) {
        String packId = CardPack.alias(face.type().packId);
        String key = "gui.minopp.pack." + packId;
        String name = StatCollector.canTranslate(key) ? Chat.translatable(key)
            .getUnformattedText() : packId;
        return EnumChatFormatting.BLUE.toString() + EnumChatFormatting.ITALIC + name;
    }

    public static List<String> lines(FontRenderer font, CardGame game, Card card, int width) {
        CardFace face = game.face(card);
        List<String> lines = new ArrayList<>();
        lines.add(title(game, card));
        lines.addAll(
            font.listFormattedStringToWidth(
                EnumChatFormatting.GRAY + description(face),
                Math.max(80, Math.min(280, width - 32))));
        lines.add(packLine(face));
        return lines;
    }
}
