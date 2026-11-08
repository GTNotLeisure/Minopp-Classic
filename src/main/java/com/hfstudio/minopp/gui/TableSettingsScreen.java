package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Mouse;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.CardColor;
import com.hfstudio.minopp.game.CardFace;
import com.hfstudio.minopp.game.CardPack;
import com.hfstudio.minopp.game.CardTypes;
import com.hfstudio.minopp.game.GameRules;
import com.hfstudio.minopp.game.TableConfig;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.network.C2SSeatControlPacket;
import com.hfstudio.minopp.render.CardRenderer;
import com.hfstudio.minopp.render.ThemeManager;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Table setup: rules (all mechanics can be switched individually), card packs (enable whole packs or load a preset)
 * and per-card counts (mix single cards of any pack into the deck).
 */
@SideOnly(Side.CLIENT)
public class TableSettingsScreen extends GuiScreen {

    private enum Tab {
        RULES,
        PACKS,
        CARDS
    }

    private static Tab tab = Tab.RULES;
    private static int scroll = 0;

    private final BlockPos gamePos;
    private final GuiScreen parent;

    private static final int ROW_H = 22;
    private static final int LIST_TOP = 52;

    /** One list row: a label and up to three buttons. */
    private record Row(String label, String tooltip, Object payload, Kind kind) {}

    private enum Kind {
        HEADER,
        BOOL_RULE,
        INT_RULE,
        PACK,
        COUNT
    }

    private final List<Row> rows = new ArrayList<>();

    public TableSettingsScreen(BlockPos gamePos, GuiScreen parent) {
        this.gamePos = gamePos;
        this.parent = parent;
    }

    private BlockEntityMinoTable table() {
        return ItemHandCards.getTable(mc.theWorld, gamePos);
    }

    private static String tr(String key, Object... args) {
        return Chat.translatable(key, args)
            .getFormattedText();
    }

    private int listBottom() {
        return height - 34;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - LIST_TOP) / ROW_H);
    }

    private void buildRows() {
        rows.clear();
        switch (tab) {
            case RULES -> {
                for (GameRules.Category cat : GameRules.Category.values()) {
                    rows.add(
                        new Row(
                            tr(
                                "gui.minopp.rule_category." + cat.name()
                                    .toLowerCase()),
                            null,
                            null,
                            Kind.HEADER));
                    for (GameRules.Rule r : GameRules.byCategory(cat)) {
                        rows.add(
                            new Row(
                                tr(r.langKey()),
                                r.langKey() + ".desc",
                                r,
                                r.bool() ? Kind.BOOL_RULE : Kind.INT_RULE));
                    }
                }
            }
            case PACKS -> {
                for (CardPack pack : CardPack.all()) {
                    if (pack.backSideOnly) continue;
                    rows.add(new Row(tr(pack.langKey()), pack.langKey() + ".desc", pack, Kind.PACK));
                }
            }
            case CARDS -> {
                for (CardPack pack : CardPack.all()) {
                    rows.add(new Row(tr(pack.langKey()), null, null, Kind.HEADER));
                    for (CardPack.Entry entry : pack.entries) {
                        rows.add(new Row(entryLabel(entry), null, new Object[] { pack, entry }, Kind.COUNT));
                    }
                }
            }
        }
        scroll = Math.max(0, Math.min(scroll, rows.size() - visibleRows()));
    }

    private static String entryLabel(CardPack.Entry entry) {
        String name = CardTypes.get(entry.typeId()).numbered ? numberLabel(entry.numbers())
            : Chat.translatable("game.minopp.card.type." + entry.typeId())
                .getFormattedText();
        String per = entry.isWild() ? ""
            : " " + Chat.translatable(entry.dark() ? "gui.minopp.cards.per_dark_color" : "gui.minopp.cards.per_color")
                .getFormattedText();
        return name + EnumChatFormatting.GRAY + per;
    }

    private static String numberLabel(int[] numbers) {
        if (numbers.length == 1) return String.valueOf(numbers[0]);
        StringBuilder sb = new StringBuilder();
        for (int n : numbers) {
            if (sb.length() > 0) sb.append(',');
            sb.append(n);
        }
        return sb.toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        buttonList.clear();
        buildRows();
        int tw = 90;
        for (Tab t : Tab.values()) {
            GuiButton b = new GuiButton(
                900 + t.ordinal(),
                width / 2 - (tw * 3 + 8) / 2 + t.ordinal() * (tw + 4),
                6,
                tw,
                20,
                tr(
                    "gui.minopp.settings.tab." + t.name()
                        .toLowerCase()));
            b.enabled = t != tab;
            buttonList.add(b);
        }
        buttonList.add(new GuiButton(999, width / 2 - 50, height - 26, 100, 20, tr("gui.done")));

        BlockEntityMinoTable te = table();
        boolean editable = te != null && te.game == null;
        int right = width / 2 + 170;
        for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int y = LIST_TOP + i * ROW_H;
            int id = (scroll + i) * 4;
            switch (row.kind) {
                case BOOL_RULE -> buttonList.add(button(id, right - 50, y, 50, "", editable));
                case INT_RULE -> {
                    buttonList.add(button(id, right - 70, y, 20, "-", editable));
                    buttonList.add(button(id + 1, right - 20, y, 20, "+", editable));
                }
                case PACK -> {
                    buttonList.add(button(id, right - 120, y, 50, "", editable));
                    buttonList.add(button(id + 1, right - 66, y, 66, tr("gui.minopp.packs.preset"), editable));
                }
                case COUNT -> {
                    buttonList.add(button(id, right - 90, y, 20, "-", editable));
                    buttonList.add(button(id + 1, right - 40, y, 20, "+", editable));
                    buttonList.add(button(id + 2, right - 18, y, 18, "R", editable));
                }
                default -> {}
            }
        }
    }

    private static GuiButton button(int id, int x, int y, int w, String text, boolean enabled) {
        GuiButton b = new GuiButton(id, x, y, w, 20, text);
        b.enabled = enabled;
        return b;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 999) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id >= 900) {
            tab = Tab.values()[button.id - 900];
            scroll = 0;
            initGui();
            return;
        }
        BlockEntityMinoTable te = table();
        if (te == null) return;
        TableConfig cfg = te.config;
        Row row = rows.get(button.id / 4);
        int sub = button.id % 4;
        switch (row.kind) {
            case BOOL_RULE -> {
                GameRules.Rule r = (GameRules.Rule) row.payload;
                C2SSeatControlPacket.Client.sendRuleC2S(gamePos, r.id(), cfg.getInt(r.id()) == 0 ? 1 : 0);
            }
            case INT_RULE -> {
                GameRules.Rule r = (GameRules.Rule) row.payload;
                int step = isShiftKeyDown() ? 5 : 1;
                C2SSeatControlPacket.Client
                    .sendRuleC2S(gamePos, r.id(), r.clamp(cfg.getInt(r.id()) + (sub == 0 ? -step : step)));
            }
            case PACK -> {
                CardPack pack = (CardPack) row.payload;
                if (sub == 0) C2SSeatControlPacket.Client.sendPackC2S(gamePos, pack.id, !cfg.isPackEnabled(pack.id));
                else C2SSeatControlPacket.Client.sendPresetC2S(gamePos, pack.id);
            }
            case COUNT -> {
                Object[] p = (Object[]) row.payload;
                CardPack pack = (CardPack) p[0];
                CardPack.Entry entry = (CardPack.Entry) p[1];
                String key = TableConfig.countKey(pack, entry);
                int current = cfg.getCount(pack, entry);
                int step = isShiftKeyDown() ? 5 : 1;
                if (sub == 2) C2SSeatControlPacket.Client.sendCountC2S(gamePos, key, -1);
                else C2SSeatControlPacket.Client
                    .sendCountC2S(gamePos, key, Math.max(0, current + (sub == 0 ? -step : step)));
            }
            default -> {}
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int max = Math.max(0, rows.size() - visibleRows());
            scroll = Math.max(0, Math.min(max, scroll - Integer.signum(wheel) * 2));
            initGui();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        BlockEntityMinoTable te = table();
        if (te == null) {
            mc.displayGuiScreen(null);
            return;
        }
        TableConfig cfg = te.config;
        int left = width / 2 - 170, right = width / 2 + 170;
        GuiDraw.fill(left - 4, LIST_TOP - 22, right + 4, listBottom() + 2, 0xC0101010);
        String info = tr("gui.minopp.settings.deck_size", cfg.countDeckSize());
        if (te.game != null) info += "  " + EnumChatFormatting.RED + tr("gui.minopp.settings.locked");
        GuiDraw.drawString(fontRendererObj, info, left, LIST_TOP - 16, 0xFFFFFFAA);

        String tooltip = null;
        for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int y = LIST_TOP + i * ROW_H;
            if (row.kind == Kind.HEADER) {
                GuiDraw.fill(left, y + 2, right, y + 18, 0x80335577);
                GuiDraw.drawString(fontRendererObj, EnumChatFormatting.BOLD + row.label, left + 4, y + 6, 0xFFFFFFFF);
                continue;
            }
            int labelX = left + 4;
            if (row.kind == Kind.COUNT) {
                CardPack.Entry entry = (CardPack.Entry) ((Object[]) row.payload)[1];
                CardColor color = entry.isWild() ? CardColor.WILD : (entry.dark() ? CardColor.PINK : CardColor.RED);
                int number = entry.numbers().length > 0 ? entry.numbers()[0] : -1;
                CardRenderer.drawFace2D(
                    new CardFace(entry.typeId(), color, number, entry.flexColor() ? color.flexPartner() : null),
                    ThemeManager.INSTANCE.selected(),
                    left + 2,
                    y + 1,
                    12,
                    19);
                labelX += 14;
            }
            GuiDraw.drawString(fontRendererObj, row.label, labelX, y + 6, 0xFFFFFFFF);
            if (mouseX >= left && mouseX < right - 130 && mouseY >= y && mouseY < y + ROW_H && row.tooltip != null) {
                tooltip = row.tooltip;
            }
            switch (row.kind) {
                case BOOL_RULE -> setLabel(scroll + i, 0, cfg.getInt(((GameRules.Rule) row.payload).id()) != 0);
                case INT_RULE -> GuiDraw.drawCenteredString(
                    fontRendererObj,
                    String.valueOf(cfg.getInt(((GameRules.Rule) row.payload).id())),
                    right - 35,
                    y + 6,
                    0xFFFFFFFF);
                case PACK -> setLabel(scroll + i, 0, cfg.isPackEnabled(((CardPack) row.payload).id));
                case COUNT -> {
                    Object[] p = (Object[]) row.payload;
                    CardPack pack = (CardPack) p[0];
                    CardPack.Entry entry = (CardPack.Entry) p[1];
                    int count = cfg.getCount(pack, entry);
                    boolean override = cfg.hasOverride(pack, entry);
                    GuiDraw.drawCenteredString(
                        fontRendererObj,
                        String.valueOf(count),
                        right - 55,
                        y + 6,
                        override ? 0xFFFFD040 : (count > 0 ? 0xFFFFFFFF : 0xFF777777));
                }
                default -> {}
            }
        }
        if (rows.size() > visibleRows()) {
            int barH = Math.max(10, (listBottom() - LIST_TOP) * visibleRows() / rows.size());
            int barY = LIST_TOP + (listBottom() - LIST_TOP - barH) * scroll / Math.max(1, rows.size() - visibleRows());
            GuiDraw.fill(right + 1, barY, right + 3, barY + barH, 0xFFAAAAAA);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (tooltip != null) {
            String text = tr(tooltip);
            if (!text.equals(tooltip)) {
                List<?> lines = fontRendererObj.listFormattedStringToWidth(text, 260);
                int h = lines.size() * 10;
                GuiDraw.fill(mouseX + 8, mouseY - 2, mouseX + 272, mouseY + h + 2, 0xF0100010);
                for (int k = 0; k < lines.size(); k++) {
                    GuiDraw
                        .drawString(fontRendererObj, (String) lines.get(k), mouseX + 12, mouseY + k * 10, 0xFFFFFFFF);
                }
            }
        }
    }

    private void setLabel(int rowIndex, int sub, boolean on) {
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (b.id == rowIndex * 4 + sub) {
                b.displayString = on ? EnumChatFormatting.GREEN + tr("options.on")
                    : EnumChatFormatting.RED + tr("options.off");
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
