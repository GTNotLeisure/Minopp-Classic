package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.config.DesignStore;
import com.hfstudio.minopp.game.CardColor;
import com.hfstudio.minopp.game.CardFace;
import com.hfstudio.minopp.game.CardPack;
import com.hfstudio.minopp.game.CardType;
import com.hfstudio.minopp.game.CardTypes;
import com.hfstudio.minopp.game.GameRules;
import com.hfstudio.minopp.game.TableConfig;
import com.hfstudio.minopp.gui.ui.SmoothScroll;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.network.C2SSeatControlPacket;
import com.hfstudio.minopp.render.CardRenderer;
import com.hfstudio.minopp.render.ThemeManager;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Table setup in one place: saved designs, card packs, house rules, single cards, table options and card style.
 * Every option is a tile: left click toggles / applies, right click adjusts counts or toggles single packs, the hovered
 * tile is explained in the info panel and in a tooltip.
 */
@SideOnly(Side.CLIENT)
public class GameSettingsScreen extends GuiScreen {

    public enum Tab {

        DESIGNS("tab_designs"),
        PACKS("tab_packs"),
        RULES("tab_rules"),
        CARDS("tab_cards"),
        TABLE("tab_table"),
        STYLE("tab_style");

        final String icon;

        Tab(String icon) {
            this.icon = icon;
        }

        String label() {
            return Chat.translatable("gui.minopp.settings.tab." + name().toLowerCase())
                .getFormattedText();
        }
    }

    private enum Kind {
        HEADER,
        DESIGN,
        PACK,
        RULE,
        CARD,
        THEME,
        INFO
    }

    private static final class Tile {

        final String id;
        final Kind kind;
        final Object payload;
        int x, y, w, h;
        String title = "";
        final List<String> desc = new ArrayList<>();
        final List<String> controls = new ArrayList<>();

        Tile(String id, Kind kind, Object payload) {
            this.id = id;
            this.kind = kind;
            this.payload = payload;
        }
    }

    private static Tab tab = Tab.PACKS;

    private final BlockPos gamePos;
    private final GuiScreen parent;
    private final Map<Tab, SmoothScroll> scrolls = new EnumMap<>(Tab.class);
    private final Map<String, Float> hoverAnim = new HashMap<>();
    private final List<Tile> tiles = new ArrayList<>();
    private Tile hovered;
    private Tile infoTile;
    private long hoverStart;
    private long lastFrame = System.nanoTime();

    // layout
    private int contentX, contentY, contentW, contentH, infoX, infoW;
    private boolean showInfo;

    public GameSettingsScreen(BlockPos gamePos, GuiScreen parent) {
        this(gamePos, parent, null);
    }

    /** Dev preview (-Dminopp.uiPreview): renders against a detached table without a world or server. */
    private final BlockEntityMinoTable previewTable;
    private long previewSwitch = System.currentTimeMillis();

    public GameSettingsScreen(BlockPos gamePos, GuiScreen parent, BlockEntityMinoTable previewTable) {
        this.gamePos = gamePos;
        this.parent = parent;
        this.previewTable = previewTable;
        for (Tab t : Tab.values()) scrolls.put(t, new SmoothScroll());
    }

    public static GameSettingsScreen preview() {
        BlockEntityMinoTable te = new BlockEntityMinoTable();
        te.config.set(GameRules.MAX_PLAYERS, 8);
        te.config.setPackEnabled(CardPack.NO_MERCY, true);
        te.config.setCount("weekend:wild_time_balloon", 2);
        for (int i = 0; i < 5; i++) {
            te.seats[i] = new com.hfstudio.minopp.game.CardPlayer(java.util.UUID.randomUUID(), "Player" + i);
        }
        tab = Tab.PACKS;
        return new GameSettingsScreen(new BlockPos(0, 0, 0), null, te);
    }

    public static void open(BlockPos gamePos, GuiScreen parent, Tab initial) {
        if (initial != null) tab = initial;
        net.minecraft.client.Minecraft.getMinecraft()
            .displayGuiScreen(new GameSettingsScreen(gamePos, parent));
    }

    private BlockEntityMinoTable table() {
        if (previewTable != null) return previewTable;
        return mc.theWorld == null ? null : ItemHandCards.getTable(mc.theWorld, gamePos);
    }

    private boolean editable() {
        BlockEntityMinoTable te = table();
        return te != null && te.game == null;
    }

    private static String tr(String key, Object... args) {
        return Chat.translatable(key, args)
            .getFormattedText();
    }

    private static boolean hasTr(String key) {
        return !net.minecraft.util.StatCollector.translateToLocal(key)
            .equals(key);
    }

    // =================================================================== layout

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        int margin = 8;
        showInfo = width >= 380;
        infoW = showInfo ? Math.max(110, Math.min(170, (int) (width * 0.3))) : 0;
        infoX = width - margin - infoW;
        contentX = margin;
        contentY = 62;
        contentW = (showInfo ? infoX - 6 : width - margin) - contentX;
        contentH = height - 30 - contentY;
    }

    private int viewX() {
        return contentX + 6;
    }

    private int viewY() {
        return contentY + 6;
    }

    private int viewW() {
        return contentW - 18;
    }

    private int viewH() {
        return contentH - 12;
    }

    /** Lays tiles of one size out in rows; returns the y after the last row. */
    private int grid(List<Tile> list, int startY, int tileW, int tileH, int gap) {
        int cols = Math.max(1, (viewW() + gap) / (tileW + gap));
        int used = cols * tileW + (cols - 1) * gap;
        int x0 = (viewW() - used) / 2;
        int i = 0;
        int y = startY;
        for (Tile t : list) {
            t.x = x0 + (i % cols) * (tileW + gap);
            t.y = startY + (i / cols) * (tileH + gap);
            t.w = tileW;
            t.h = tileH;
            y = t.y + tileH + gap;
            i++;
        }
        return list.isEmpty() ? startY : y;
    }

    private int header(String text, int y) {
        Tile h = new Tile("h:" + text + y, Kind.HEADER, null);
        h.title = text;
        h.x = 0;
        h.y = y;
        h.w = viewW();
        h.h = 14;
        tiles.add(h);
        return y + 18;
    }

    private void buildTiles() {
        tiles.clear();
        BlockEntityMinoTable te = table();
        if (te == null) return;
        TableConfig cfg = te.config;
        int y = 2;
        switch (tab) {
            case DESIGNS -> {
                List<Tile> list = new ArrayList<>();
                for (int i = 0; i < DesignStore.SLOTS; i++) {
                    DesignStore.Design d = DesignStore.get(i);
                    Tile t = new Tile("design:" + i, Kind.DESIGN, i);
                    if (d != null) {
                        t.title = tr("gui.minopp.design.name", d.name);
                        t.desc.add(describePacks(d.config));
                        t.desc.add(
                            tr(
                                "gui.minopp.design.summary",
                                d.config.countDeckSize(),
                                d.config.getInt(GameRules.MAX_PLAYERS)));
                        t.controls.add(tr("gui.minopp.controls.design_apply"));
                        t.controls.add(tr("gui.minopp.controls.design_overwrite"));
                        t.controls.add(tr("gui.minopp.controls.design_delete"));
                    } else {
                        t.title = tr("gui.minopp.design.empty");
                        t.desc.add(tr("gui.minopp.design.empty.desc"));
                        t.controls.add(tr("gui.minopp.controls.design_save"));
                    }
                    list.add(t);
                }
                tiles.addAll(list);
                grid(list, y, 112, 72, 8);
            }
            case PACKS -> {
                List<Tile> list = new ArrayList<>();
                for (CardPack pack : CardPack.all()) {
                    if (pack.backSideOnly) continue;
                    Tile t = new Tile("pack:" + pack.id, Kind.PACK, pack);
                    t.title = tr(pack.langKey());
                    t.desc.add(tr(pack.langKey() + ".desc"));
                    t.controls.add(tr("gui.minopp.controls.pack_left"));
                    t.controls.add(tr("gui.minopp.controls.pack_right"));
                    list.add(t);
                }
                tiles.addAll(list);
                grid(list, y, 64, 70, 8);
            }
            case RULES -> {
                for (GameRules.Category cat : new GameRules.Category[] { GameRules.Category.HOUSE,
                    GameRules.Category.BASIC, GameRules.Category.NO_MERCY, GameRules.Category.MODES }) {
                    y = header(
                        tr(
                            "gui.minopp.rule_category." + cat.name()
                                .toLowerCase()),
                        y);
                    y = grid(ruleTiles(cat), y, 58, 62, 8) + 2;
                }
            }
            case CARDS -> {
                for (CardPack pack : CardPack.all()) {
                    y = header(tr(pack.langKey()), y);
                    List<Tile> list = new ArrayList<>();
                    for (CardPack.Entry entry : pack.entries) {
                        Tile t = new Tile(
                            "card:" + TableConfig.countKey(pack, entry),
                            Kind.CARD,
                            new Object[] { pack, entry });
                        CardType type = CardTypes.get(entry.typeId());
                        t.title = type.numbered ? tr("gui.minopp.cards.numbers", numbers(entry.numbers()))
                            : tr("game.minopp.card.type." + entry.typeId());
                        String descKey = "game.minopp.card.type." + entry.typeId() + ".desc";
                        if (hasTr(descKey)) t.desc.add(tr(descKey));
                        int def = entry.count();
                        if (entry.isWild()) {
                            t.desc.add(tr("gui.minopp.cards.count_wild", cfg.getCount(pack, entry), def));
                        } else {
                            int perColor = cfg.getCount(pack, entry);
                            t.desc.add(
                                tr(
                                    "gui.minopp.cards.count_color",
                                    perColor,
                                    perColor * 4 * Math.max(1, entry.numbers().length),
                                    def));
                        }
                        t.controls.add(tr("gui.minopp.controls.card_left"));
                        t.controls.add(tr("gui.minopp.controls.card_right"));
                        t.controls.add(tr("gui.minopp.controls.card_ctrl"));
                        list.add(t);
                    }
                    tiles.addAll(list);
                    y = grid(list, y, 34, 52, 6) + 2;
                }
            }
            case TABLE -> {
                y = header(tr("gui.minopp.rule_category.table"), y);
                y = grid(ruleTiles(GameRules.Category.TABLE), y, 58, 62, 8) + 2;
                Tile info = new Tile("deckinfo", Kind.INFO, null);
                info.title = tr("gui.minopp.table.deck_title");
                tiles.add(info);
                info.x = 0;
                info.y = y;
                info.w = viewW();
                info.h = 40;
            }
            case STYLE -> {
                List<Tile> list = new ArrayList<>();
                for (ThemeManager.ThemeInfo theme : ThemeManager.INSTANCE.getThemes()) {
                    Tile t = new Tile("theme:" + theme.id(), Kind.THEME, theme);
                    t.title = theme.name();
                    t.desc.add(tr("gui.minopp.style.desc"));
                    t.controls.add(tr("gui.minopp.controls.theme"));
                    list.add(t);
                }
                tiles.addAll(list);
                grid(list, y, 112, 72, 8);
            }
        }
    }

    private List<Tile> ruleTiles(GameRules.Category cat) {
        List<Tile> list = new ArrayList<>();
        for (GameRules.Rule r : GameRules.byCategory(cat)) {
            Tile t = new Tile("rule:" + r.id(), Kind.RULE, r);
            t.title = tr(r.langKey());
            t.desc.add(tr(r.langKey() + ".desc"));
            if (r.bool()) {
                t.controls.add(tr("gui.minopp.controls.rule_toggle"));
            } else {
                t.desc.add(tr("gui.minopp.rule.range", r.min(), r.max()));
                t.controls.add(tr("gui.minopp.controls.rule_int"));
            }
            list.add(t);
        }
        tiles.addAll(list);
        return list;
    }

    private static String numbers(int[] numbers) {
        if (numbers.length == 0) return "";
        if (numbers.length > 3 && numbers[numbers.length - 1] - numbers[0] == numbers.length - 1) {
            return numbers[0] + "-" + numbers[numbers.length - 1];
        }
        StringBuilder sb = new StringBuilder();
        for (int n : numbers) {
            if (sb.length() > 0) sb.append(',');
            sb.append(n);
        }
        return sb.toString();
    }

    private static String describePacks(TableConfig cfg) {
        StringBuilder sb = new StringBuilder();
        for (String id : cfg.packs) {
            CardPack p = CardPack.get(id);
            if (p == null) continue;
            if (sb.length() > 0) sb.append(" + ");
            sb.append(tr(p.langKey()));
        }
        if (!cfg.counts.isEmpty()) sb.append(" (+")
            .append(cfg.counts.size())
            .append(')');
        return sb.toString();
    }

    private int contentHeight() {
        int max = 0;
        for (Tile t : tiles) max = Math.max(max, t.y + t.h);
        return max + 6;
    }

    // =================================================================== rendering

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        BlockEntityMinoTable te = table();
        if (te == null) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (previewTable != null && System.currentTimeMillis() - previewSwitch > 6000) {
            previewSwitch = System.currentTimeMillis();
            tab = Tab.values()[(tab.ordinal() + 1) % Tab.values().length];
        }
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;

        buildTiles();
        SmoothScroll scroll = scrolls.get(tab);
        scroll.setBounds(contentHeight(), viewH());
        float offset = scroll.update();

        UiDraw.background(width, height);
        UiDraw.outlinedText(
            fontRendererObj,
            tr("gui.minopp.settings.title"),
            10,
            8,
            2f,
            UiDraw.TITLE_YELLOW,
            UiDraw.OUTLINE_DARK);
        String sub = te.game != null ? EnumChatFormatting.RED + tr("gui.minopp.settings.locked")
            : tr(
                "gui.minopp.settings.subtitle",
                te.getPlayersList()
                    .size(),
                te.seatCount());
        fontRendererObj.drawStringWithShadow(sub, width - 10 - fontRendererObj.getStringWidth(sub), 14, 0xFFFFFFFF);

        drawTabs(mouseX, mouseY, dt);

        UiDraw.panel(contentX, contentY, contentX + contentW, contentY + contentH);
        hovered = null;
        boolean inView = mouseX >= viewX() && mouseX < viewX() + viewW()
            && mouseY >= viewY()
            && mouseY < viewY() + viewH();
        UiDraw.scissor(viewX() - 2, viewY(), viewW() + 4, viewH());
        GL11.glPushMatrix();
        GL11.glTranslatef(viewX(), viewY() - offset, 0);
        for (Tile t : tiles) {
            int sx = viewX() + t.x, sy = (int) (viewY() + t.y - offset);
            if (sy + t.h < viewY() - 10 || sy > viewY() + viewH() + 10) continue;
            boolean hover = inView && !scroll.isDragging()
                && t.kind != Kind.HEADER
                && mouseX >= sx
                && mouseX < sx + t.w
                && mouseY >= sy
                && mouseY < sy + t.h;
            if (hover) hovered = t;
            float a = hoverAnim.getOrDefault(t.id, 0f);
            a += ((hover ? 1f : 0f) - a) * Math.min(1, dt * 16);
            hoverAnim.put(t.id, a);
            drawTile(te, t, a);
        }
        GL11.glPopMatrix();
        UiDraw.endScissor();
        int barX = contentX + contentW - 9;
        boolean barHover = mouseX >= barX - 3 && mouseX <= barX + 7 && mouseY >= viewY() && mouseY <= viewY() + viewH();
        scroll.drawBar(barX, viewY(), viewH(), barHover);

        if (hovered != infoTile && hovered != null) {
            infoTile = hovered;
            hoverStart = System.currentTimeMillis();
        }
        if (showInfo) drawInfoPanel(te);
        drawBottomBar(mouseX, mouseY);

        if (hovered != null && System.currentTimeMillis() - hoverStart > 350) {
            List<String> lines = new ArrayList<>();
            lines.add(hovered.title);
            for (String d : hovered.desc)
                for (Object l : fontRendererObj.listFormattedStringToWidth(d, 200)) lines.add((String) l);
            for (String c : hovered.controls) lines.add(EnumChatFormatting.GRAY + c);
            UiDraw.tooltip(fontRendererObj, lines, mouseX, mouseY, width, height);
        }
    }

    private int tabWidth() {
        return Math.min(64, (contentW - 30) / Tab.values().length);
    }

    private int tabX(int i) {
        return contentX + 14 + i * (tabWidth() + 2);
    }

    private void drawTabs(int mouseX, int mouseY, float dt) {
        int tw = tabWidth();
        fontRendererObj.drawStringWithShadow("Q", contentX + 2, 42, 0xFFBBBBBB);
        fontRendererObj.drawStringWithShadow("E", tabX(Tab.values().length) + 2, 42, 0xFFBBBBBB);
        for (Tab t : Tab.values()) {
            int x = tabX(t.ordinal()), y = 32;
            boolean sel = t == tab;
            boolean hover = mouseX >= x && mouseX < x + tw && mouseY >= y && mouseY < y + 28;
            if (sel) {
                UiDraw.roundFrame(x - 1, y - 1, x + tw + 1, y + 29, 5, 2, UiDraw.GLOW, 0xFFE0402A, 0xFFA01A14);
            } else {
                UiDraw.roundRect(
                    x,
                    y,
                    x + tw,
                    y + 28,
                    5,
                    hover ? 0xD0A02020 : 0xB0801414,
                    hover ? 0xD0701010 : 0xB0600C0C);
            }
            UiDraw.icon(t.icon, x + tw / 2f - 8, y + 1, 16);
            UiDraw.fittedCentered(
                fontRendererObj,
                t.label(),
                x + tw / 2f,
                y + 18,
                tw - 4,
                sel ? UiDraw.TITLE_YELLOW : 0xFFFFFFFF);
        }
    }

    private void drawTile(BlockEntityMinoTable te, Tile t, float hoverA) {
        TableConfig cfg = te.config;
        boolean editable = te.game == null;
        if (t.kind == Kind.HEADER) {
            UiDraw.roundRect(t.x, t.y, t.x + t.w, t.y + t.h, 4, 0x60000000);
            fontRendererObj.drawStringWithShadow(t.title, t.x + 6, t.y + 3, UiDraw.TITLE_YELLOW);
            GL11.glColor4f(1, 1, 1, 1);
            return;
        }
        if (t.kind == Kind.INFO) {
            drawDeckInfo(te, t);
            return;
        }
        int lift = (int) (hoverA * 2);
        int x1 = t.x, y1 = t.y - lift, x2 = t.x + t.w, y2 = t.y + t.h - lift;
        Boolean checked = null;
        String count = null;
        boolean four = false, locked = false, dim = false;
        customCount = false;

        // glow when hovered
        if (hoverA > 0.02f) {
            int glowA = (int) (hoverA * 200);
            UiDraw.roundRect(x1 - 3, y1 - 3, x2 + 3, y2 + 3, 7, (glowA << 24) | (UiDraw.GLOW & 0xFFFFFF));
        }
        switch (t.kind) {
            case DESIGN -> {
                int slot = (Integer) t.payload;
                DesignStore.Design d = DesignStore.get(slot);
                if (d == null) {
                    boolean green = slot % 2 == 0;
                    UiDraw.roundFrame(
                        x1,
                        y1,
                        x2,
                        y2,
                        6,
                        2,
                        0xFFFFFFFF,
                        green ? 0xFF6CC24A : 0xFF2A9AE0,
                        green ? 0xFF2F8A2A : 0xFF0A5AB0);
                    UiDraw.image("plus", (x1 + x2) / 2f - 12, (y1 + y2) / 2f - 12, 24, 24);
                } else {
                    UiDraw.roundFrame(
                        x1,
                        y1,
                        x2,
                        y2,
                        6,
                        2,
                        0xFFFFFFFF,
                        slot % 3 == 1 ? 0xFF4A3A10 : 0xFFF07A30,
                        slot % 3 == 1 ? 0xFF1A1206 : 0xFFC0201A);
                    drawFan(packPreview(d.config), ThemeManager.INSTANCE.selected(), (x1 + x2) / 2f, y2 - 18, 22);
                    UiDraw.fittedCentered(fontRendererObj, t.title, (x1 + x2) / 2f, y2 - 12, t.w - 8, 0xFFFFFFFF);
                    checked = d.config.sameAs(cfg) ? Boolean.TRUE : null;
                }
            }
            case PACK -> {
                CardPack pack = (CardPack) t.payload;
                boolean on = cfg.isPackEnabled(pack.id);
                UiDraw.roundFrame(x1, y1, x2, y2, 6, 2, 0xFFFFFFFF, UiDraw.TILE_TOP, UiDraw.TILE_BOTTOM);
                drawFan(packFaces(pack), ThemeManager.INSTANCE.selected(), (x1 + x2) / 2f, y1 + 46, 20);
                UiDraw.fittedCentered(fontRendererObj, t.title, (x1 + x2) / 2f, y2 - 11, t.w - 6, 0xFFFFFFFF);
                checked = on;
                locked = !editable;
            }
            case RULE -> {
                GameRules.Rule r = (GameRules.Rule) t.payload;
                int v = cfg.getInt(r.id());
                UiDraw.roundFrame(x1, y1, x2, y2, 6, 2, 0xFFFFFFFF, UiDraw.TILE_TOP, UiDraw.TILE_BOTTOM);
                // numbered rules: icon on top, value pill, label; switches: icon centered above the label
                int iconY = r.bool() ? y1 + 2 + (t.h - 16 - 32) / 2 : y1 + 3;
                UiDraw.icon(r.id(), (x1 + x2) / 2f - 16, iconY, 32);
                UiDraw.fittedCentered(fontRendererObj, t.title, (x1 + x2) / 2f, y2 - 12, t.w - 6, 0xFFFFFFFF);
                if (r.bool()) {
                    checked = v != 0;
                    dim = v == 0;
                } else {
                    // value sits centered between the icon and the label
                    count = v == 0 && r.id()
                        .equals(GameRules.DECK_COPIES) ? "A" : String.valueOf(v);
                }
                locked = !editable;
            }
            case CARD -> {
                Object[] p = (Object[]) t.payload;
                CardPack pack = (CardPack) p[0];
                CardPack.Entry entry = (CardPack.Entry) p[1];
                int c = cfg.getCount(pack, entry);
                UiDraw.roundRect(x1, y1, x2, y2, 4, 0x80000000);
                CardRenderer.drawFace2D(
                    sampleFace(entry, 0),
                    ThemeManager.INSTANCE.selected(),
                    x1 + 2,
                    y1 + 2,
                    t.w - 4,
                    t.h - 4);
                checked = c > 0;
                dim = c == 0;
                count = String.valueOf(c);
                four = !entry.isWild();
                locked = !editable;
                customCount = cfg.hasOverride(pack, entry);
            }
            case THEME -> {
                ThemeManager.ThemeInfo theme = (ThemeManager.ThemeInfo) t.payload;
                UiDraw.roundFrame(x1, y1, x2, y2, 6, 2, 0xFFFFFFFF, 0xFFF0A040, 0xFFD06020);
                List<CardFace> faces = List.of(
                    new CardFace("number", CardColor.RED, 1),
                    new CardFace("number", CardColor.YELLOW, 2),
                    new CardFace("number", CardColor.GREEN, 3),
                    new CardFace("number", CardColor.BLUE, 4),
                    new CardFace("wild_draw4", CardColor.WILD, -1));
                drawFan(faces, theme.id(), x1 + t.w * 0.42f, y2 - 16, 24);
                CardRenderer.drawBack2D(theme.id(), false, x2 - 30, y1 + 8, 24, 36);
                UiDraw.fittedCentered(fontRendererObj, t.title, (x1 + x2) / 2f, y2 - 12, t.w - 8, 0xFFFFFFFF);
                checked = theme.id()
                    .equals(ThemeManager.INSTANCE.selected()) ? Boolean.TRUE : null;
            }
            default -> {}
        }
        if (dim) UiDraw.roundRect(x1, y1, x2, y2, 6, 0x5A000000);
        if (checked != null) {
            if (checked || t.kind == Kind.RULE || t.kind == Kind.PACK) {
                UiDraw.image(checked ? "badge_check" : "badge_check_off", x2 - 10, y1 - 3, 12, 12);
            }
        }
        if (four) UiDraw.image("badge_four", x1 + 2, y1 + 2, 9, 9);
        if (count != null) {
            // centered at the bottom, above the label (rules) or at the card's foot (cards)
            int pillY = t.kind == Kind.CARD ? y2 - 11 : y2 - 24;
            valuePill(count, (x1 + x2) / 2, pillY);
        }
        if (locked) UiDraw.image("badge_lock", x1 + 2, four ? y1 + 12 : y1 + 2, 10, 10);
    }

    /** Count of the tile being drawn differs from the pack default (pill turns white). */
    private boolean customCount;

    private void valuePill(String value, int cx, int y) {
        int textW = fontRendererObj.getStringWidth(value) - 1;
        int pillW = textW + 8;
        int x1 = cx - pillW / 2;
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, 50);
        UiDraw.roundFrame(
            x1,
            y,
            x1 + pillW,
            y + 11,
            4,
            1,
            UiDraw.OUTLINE_DARK,
            customCount ? 0xFFFFFFFF : 0xFFFFE15A,
            customCount ? 0xFFD8D8D8 : 0xFFF0B020);
        fontRendererObj.drawString(value, x1 + 4, y + 2, 0xFF3A1A00);
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void drawDeckInfo(BlockEntityMinoTable te, Tile t) {
        TableConfig cfg = te.config;
        UiDraw.roundRect(t.x, t.y, t.x + t.w, t.y + t.h, 5, 0x70000000);
        int size = cfg.countDeckSize();
        int players = Math.max(
            te.getPlayersList()
                .size(),
            2);
        int seatCopies = cfg.effectiveCopies(te.seatCount(), size);
        int copies = cfg.effectiveCopies(players, size);
        fontRendererObj.drawStringWithShadow(t.title, t.x + 6, t.y + 4, UiDraw.TITLE_YELLOW);
        fontRendererObj.drawStringWithShadow(
            tr("gui.minopp.table.deck_now", size, copies, size * copies, players),
            t.x + 6,
            t.y + 16,
            0xFFFFFFFF);
        fontRendererObj.drawStringWithShadow(
            tr("gui.minopp.table.deck_full", te.seatCount(), seatCopies, size * seatCopies),
            t.x + 6,
            t.y + 27,
            0xFFCCCCCC);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static CardFace sampleFace(CardPack.Entry entry, int variant) {
        CardType type = CardTypes.get(entry.typeId());
        CardColor color = type.wild ? CardColor.WILD
            : CardColor.forSide(entry.dark())
                .get(variant % 4);
        int number = entry.numbers().length > 0 ? entry.numbers()[Math.min(variant, entry.numbers().length - 1)] : -1;
        return new CardFace(type.id, color, number, entry.flexColor() ? color.flexPartner() : null);
    }

    private static List<CardFace> packFaces(CardPack pack) {
        // the most telling cards of a pack: colored action cards first, then one wild, numbers only as filler
        List<CardFace> faces = new ArrayList<>();
        List<CardPack.Entry> actions = new ArrayList<>(), wilds = new ArrayList<>(), numbers = new ArrayList<>();
        for (CardPack.Entry e : pack.entries) {
            if (CardTypes.get(e.typeId()).numbered) numbers.add(e);
            else if (e.isWild()) wilds.add(e);
            else actions.add(e);
        }
        for (int i = actions.size() - 1; i >= 0 && faces.size() < 2; i--) faces.add(0, sampleFace(actions.get(i), i));
        if (!wilds.isEmpty()) faces.add(sampleFace(wilds.get(wilds.size() - 1), 0));
        for (int i = wilds.size() - 2; i >= 0 && faces.size() < 3; i--) faces.add(0, sampleFace(wilds.get(i), 0));
        for (int i = 0; i < numbers.size() && faces.size() < 3; i++) faces.add(0, sampleFace(numbers.get(i), 2 + i));
        return faces;
    }

    private static List<CardFace> packPreview(TableConfig cfg) {
        List<CardFace> faces = new ArrayList<>();
        for (String id : cfg.packs) {
            CardPack p = CardPack.get(id);
            if (p != null && !p.entries.isEmpty())
                faces.add(sampleFace(p.entries.get(p.entries.size() - 1), faces.size()));
            if (faces.size() >= 4) break;
        }
        if (faces.isEmpty()) faces.add(new CardFace("number", CardColor.RED, 7));
        return faces;
    }

    /** Fan of cards pivoting around (cx, bottom). */
    private static void drawFan(List<CardFace> faces, String theme, float cx, float bottom, float cardW) {
        float cardH = cardW * 1.5625f;
        int n = faces.size();
        for (int i = 0; i < n; i++) {
            float angle = n == 1 ? 0 : -24 + 48f * i / (n - 1);
            GL11.glPushMatrix();
            GL11.glTranslatef(cx, bottom, 0);
            GL11.glRotatef(angle, 0, 0, 1);
            CardRenderer.drawFace2D(faces.get(i), theme, -cardW / 2, -cardH, cardW, cardH);
            GL11.glPopMatrix();
        }
    }

    private void drawInfoPanel(BlockEntityMinoTable te) {
        int x1 = infoX, y1 = contentY, x2 = infoX + infoW, y2 = contentY + contentH;
        UiDraw.panel(x1, y1, x2, y2);
        Tile t = infoTile;
        // preview area
        int py = y1 + 8, ph = Math.min(90, (y2 - y1) / 2);
        UiDraw.roundRect(x1 + 6, py, x2 - 6, py + ph, 5, 0x50000000);
        float cx = (x1 + x2) / 2f;
        if (t == null) {
            drawFan(packPreview(te.config), ThemeManager.INSTANCE.selected(), cx, py + ph - 8, 30);
        } else {
            switch (t.kind) {
                case RULE -> UiDraw.icon(((GameRules.Rule) t.payload).id(), cx - 32, py + ph / 2f - 32, 64);
                case CARD -> {
                    CardPack.Entry entry = (CardPack.Entry) ((Object[]) t.payload)[1];
                    List<CardFace> faces = new ArrayList<>();
                    for (int i = 0; i < (entry.isWild() ? 1 : 4); i++) faces.add(sampleFace(entry, i));
                    drawFan(faces, ThemeManager.INSTANCE.selected(), cx, py + ph - 6, 34);
                }
                case PACK -> drawFan(
                    packFaces((CardPack) t.payload),
                    ThemeManager.INSTANCE.selected(),
                    cx,
                    py + ph - 6,
                    32);
                case THEME -> {
                    String id = ((ThemeManager.ThemeInfo) t.payload).id();
                    drawFan(
                        List.of(
                            new CardFace("number", CardColor.RED, 5),
                            new CardFace("number", CardColor.BLUE, 3),
                            new CardFace("skip", CardColor.GREEN, -1),
                            new CardFace("wild", CardColor.WILD, -1)),
                        id,
                        cx - 8,
                        py + ph - 6,
                        30);
                    CardRenderer.drawBack2D(id, false, x2 - 40, py + 8, 28, 44);
                }
                case DESIGN -> {
                    DesignStore.Design d = DesignStore.get((Integer) t.payload);
                    if (d != null)
                        drawFan(packPreview(d.config), ThemeManager.INSTANCE.selected(), cx, py + ph - 6, 30);
                    else UiDraw.image("plus", cx - 16, py + ph / 2f - 16, 32, 32);
                }
                default -> {}
            }
        }
        int hy = py + ph + 8;
        UiDraw.roundRect(x1 + 4, hy, x2 - 4, hy + 18, 4, 0xE0180404, 0xE0281010);
        String title = t == null ? tr("gui.minopp.settings.current") : t.title;
        UiDraw.fittedCentered(fontRendererObj, title, cx, hy + 5, infoW - 14, UiDraw.TITLE_YELLOW);
        int ty = hy + 24;
        List<String> lines = new ArrayList<>();
        if (t == null) {
            lines.add(describePacks(te.config));
            lines.add(tr("gui.minopp.design.summary", te.config.countDeckSize(), te.seatCount()));
        } else {
            lines.addAll(t.desc);
        }
        for (String line : lines) {
            for (Object l : fontRendererObj.listFormattedStringToWidth(line, infoW - 16)) {
                if (ty > y2 - 12) break;
                UiDraw.scaledCentered(fontRendererObj, (String) l, cx, ty, 1f, 0xFFFFFFFF);
                ty += 10;
            }
            ty += 3;
        }
        if (t != null) {
            for (String c : t.controls) {
                for (Object l : fontRendererObj.listFormattedStringToWidth(c, infoW - 16)) {
                    if (ty > y2 - 12) break;
                    UiDraw.scaledCentered(fontRendererObj, (String) l, cx, ty, 1f, 0xFFBBBBBB);
                    ty += 10;
                }
            }
        }
    }

    private int[] pillBack = new int[4];

    private void drawBottomBar(int mouseX, int mouseY) {
        int y = height - 24;
        String back = tr("gui.minopp.settings.back");
        int bw = UiDraw.pillWidth(fontRendererObj, "Esc", back);
        int bx = width - 8 - bw;
        boolean hb = mouseX >= bx && mouseX < bx + bw && mouseY >= y && mouseY < y + 18;
        UiDraw.pill(fontRendererObj, bx, y, "Esc", back, hb);
        pillBack = new int[] { bx, y, bw, 18 };
        int x = bx - 6;
        String hint = tr(tab == Tab.DESIGNS ? "gui.minopp.settings.hint_designs" : "gui.minopp.settings.hint");
        int hw = UiDraw.pillWidth(fontRendererObj, "Q/E", hint);
        if (x - hw > 8) UiDraw.pill(fontRendererObj, x - hw, y, "Q/E", hint, false);
    }

    // =================================================================== input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        // tabs
        for (Tab t : Tab.values()) {
            int x = tabX(t.ordinal());
            if (mouseX >= x && mouseX < x + tabWidth() && mouseY >= 32 && mouseY < 60) {
                switchTab(t);
                return;
            }
        }
        if (mouseX >= pillBack[0] && mouseX < pillBack[0] + pillBack[2]
            && mouseY >= pillBack[1]
            && mouseY < pillBack[1] + pillBack[3]) {
            mc.displayGuiScreen(parent);
            return;
        }
        SmoothScroll scroll = scrolls.get(tab);
        if (scroll.mousePressed(mouseX, mouseY, contentX + contentW - 9, viewY(), viewH())) return;
        if (hovered != null) {
            onTileClicked(hovered, button);
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        scrolls.get(tab)
            .mouseDragged(mouseY, viewY(), viewH());
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        if (state >= 0) scrolls.get(tab)
            .mouseReleased();
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scrolls.get(tab)
            .scrollBy(-Integer.signum(wheel) * 36);
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_Q) {
            switchTab(Tab.values()[(tab.ordinal() + Tab.values().length - 1) % Tab.values().length]);
        } else if (key == Keyboard.KEY_E) {
            switchTab(Tab.values()[(tab.ordinal() + 1) % Tab.values().length]);
        } else if ((key == Keyboard.KEY_DELETE || key == Keyboard.KEY_X) && hovered != null
            && hovered.kind == Kind.DESIGN) {
                DesignStore.delete((Integer) hovered.payload);
            } else if (key == Keyboard.KEY_ESCAPE) {
                mc.displayGuiScreen(parent);
            } else {
                super.keyTyped(c, key);
            }
    }

    private void switchTab(Tab t) {
        if (t == tab) return;
        tab = t;
        infoTile = null;
        hovered = null;
        mc.getSoundHandler()
            .playSound(
                net.minecraft.client.audio.PositionedSoundRecord
                    .func_147674_a(new net.minecraft.util.ResourceLocation("gui.button.press"), 1.2f));
    }

    private void click() {
        mc.getSoundHandler()
            .playSound(
                net.minecraft.client.audio.PositionedSoundRecord
                    .func_147674_a(new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0f));
    }

    private void onTileClicked(Tile t, int button) {
        BlockEntityMinoTable te = table();
        if (te == null) return;
        TableConfig cfg = te.config;
        boolean shift = isShiftKeyDown();
        boolean ctrl = isCtrlKeyDown();
        if (t.kind == Kind.THEME) {
            ThemeManager.INSTANCE.select(((ThemeManager.ThemeInfo) t.payload).id());
            click();
            return;
        }
        if (t.kind == Kind.DESIGN) {
            int slot = (Integer) t.payload;
            DesignStore.Design d = DesignStore.get(slot);
            if (d == null || button == 1) {
                if (d == null || shift) {
                    DesignStore
                        .put(slot, new DesignStore.Design(d == null ? DesignStore.nextName() : d.name, cfg.copy()));
                } else {
                    DesignStore.put(slot, new DesignStore.Design(d.name, cfg.copy()));
                }
            } else if (editable()) {
                cfg.fromTag(d.config.toTag());
                if (previewTable == null) C2SSeatControlPacket.Client.sendApplyC2S(gamePos, d.config);
            }
            click();
            return;
        }
        if (!editable()) return;
        switch (t.kind) {
            case PACK -> {
                CardPack pack = (CardPack) t.payload;
                if (button == 1) {
                    boolean on = !cfg.isPackEnabled(pack.id);
                    cfg.setPackEnabled(pack.id, on);
                    if (previewTable == null) C2SSeatControlPacket.Client.sendPackC2S(gamePos, pack.id, on);
                } else {
                    cfg.resetToPreset(pack.id);
                    if (previewTable == null) C2SSeatControlPacket.Client.sendPresetC2S(gamePos, pack.id);
                }
            }
            case RULE -> {
                GameRules.Rule r = (GameRules.Rule) t.payload;
                int v = cfg.getInt(r.id());
                int nv;
                if (r.bool()) {
                    nv = v == 0 ? 1 : 0;
                } else {
                    int step = shift ? 5 : 1;
                    nv = r.clamp(v + (button == 1 ? -step : step));
                }
                cfg.set(r.id(), nv);
                if (previewTable == null) C2SSeatControlPacket.Client.sendRuleC2S(gamePos, r.id(), nv);
            }
            case CARD -> {
                Object[] p = (Object[]) t.payload;
                CardPack pack = (CardPack) p[0];
                CardPack.Entry entry = (CardPack.Entry) p[1];
                String key = TableConfig.countKey(pack, entry);
                int c = cfg.getCount(pack, entry);
                int nv;
                if (ctrl) {
                    nv = -1; // reset to the pack default
                } else if (button == 1) {
                    nv = Math.max(0, c + (shift ? -1 : 1));
                } else {
                    nv = c > 0 ? 0 : Math.max(1, entry.count());
                }
                cfg.setCount(key, nv);
                if (previewTable == null) C2SSeatControlPacket.Client.sendCountC2S(gamePos, key, nv);
            }
            default -> {}
        }
        click();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
