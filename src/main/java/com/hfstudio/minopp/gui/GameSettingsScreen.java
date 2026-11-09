package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.config.DesignStore;
import com.hfstudio.minopp.game.CardColor;
import com.hfstudio.minopp.game.CardFace;
import com.hfstudio.minopp.game.CardPack;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.CardType;
import com.hfstudio.minopp.game.CardTypes;
import com.hfstudio.minopp.game.GameRules;
import com.hfstudio.minopp.game.TableConfig;
import com.hfstudio.minopp.gui.ui.SearchMatch;
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
        String searchExtra = "";
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

    private int contentX, contentY, contentW, contentH, infoX, infoW;
    private boolean showInfo;

    private static String query = "";
    private final Map<String, Boolean> matchCache = new HashMap<>();
    private GuiTextField search, numberField;
    private String editingId;
    private Tile mouseTile;
    private String navId;
    private boolean navTabs;
    private int lastMouseX = Integer.MIN_VALUE, lastMouseY;
    private Map<CardFace, Integer> effective = new HashMap<>();
    private int[] keyQ = new int[4], keyE = new int[4];

    public GameSettingsScreen(BlockPos gamePos, GuiScreen parent) {
        this(gamePos, parent, null);
    }

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
            te.seats[i] = new CardPlayer(UUID.randomUUID(), "Player" + i);
        }
        tab = Tab.valueOf(System.getProperty("minopp.uiPreviewTab", "PACKS"));
        return new GameSettingsScreen(new BlockPos(0, 0, 0), null, te);
    }

    public static void open(BlockPos gamePos, GuiScreen parent, Tab initial) {
        if (initial != null) tab = initial;
        Minecraft.getMinecraft()
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
        return !StatCollector.translateToLocal(key)
            .equals(key);
    }

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
        search = new GuiTextField(fontRendererObj, 10, height - 21, Math.min(150, width / 3), 14);
        search.setMaxStringLength(40);
        search.setText(query);
        numberField = null;
        editingId = null;
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
        // top padding so check badges and the hover glow (which reach ~5px above a tile) are never clipped
        int y = 8;
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
                place(list, y, 112, 72, 8);
            }
            case PACKS -> {
                List<Tile> list = new ArrayList<>();
                for (CardPack pack : CardPack.all()) {
                    if (pack.backSideOnly) continue;
                    Tile t = new Tile("pack:" + pack.id, Kind.PACK, pack);
                    t.title = tr(pack.langKey());
                    t.desc.add(tr(pack.langKey() + ".desc"));
                    t.desc.add(tr("gui.minopp.pack.players", pack.minPlayers, pack.maxPlayers));
                    t.controls.add(tr("gui.minopp.controls.pack_left"));
                    t.controls.add(tr("gui.minopp.controls.pack_right"));
                    list.add(t);
                }
                place(list, y, 64, 70, 8);
            }
            case RULES -> {
                for (GameRules.Category cat : new GameRules.Category[] { GameRules.Category.HOUSE,
                    GameRules.Category.BASIC, GameRules.Category.NO_MERCY, GameRules.Category.MODES }) {
                    String name = tr(
                        "gui.minopp.rule_category." + cat.name()
                            .toLowerCase());
                    List<Tile> list = filter(ruleTiles(cat), name);
                    if (list.isEmpty()) continue;
                    y = header(name, y);
                    tiles.addAll(list);
                    y = grid(list, y, 58, 62, 8) + 2;
                }
            }
            case CARDS -> {
                effective = new HashMap<>();
                for (CardFace f : cfg.buildFaces(false)) effective.merge(f, 1, Integer::sum);
                for (CardFace f : cfg.buildFaces(true)) effective.merge(f, 1, Integer::sum);
                Set<CardFace> shown = new HashSet<>();
                for (CardPack pack : CardPack.all()) {
                    String packName = tr(pack.langKey());
                    List<Tile> list = new ArrayList<>();
                    for (CardPack.Entry entry : pack.entries) {
                        List<CardFace> faces = TableConfig.entryFaces(entry);
                        if (shown.containsAll(faces)) continue;
                        shown.addAll(faces);
                        Tile t = new Tile(
                            "card:" + TableConfig.countKey(pack, entry),
                            Kind.CARD,
                            new Object[] { pack, entry });
                        t.searchExtra = packName;
                        CardType type = CardTypes.get(entry.typeId());
                        t.title = type.numbered ? tr("gui.minopp.cards.numbers", numbers(entry.numbers()))
                            : tr("game.minopp.card.type." + entry.typeId());
                        String descKey = "game.minopp.card.type." + entry.typeId() + ".desc";
                        if (hasTr(descKey)) t.desc.add(tr(descKey));
                        int def = entry.count();
                        int c = effectiveCount(entry);
                        if (entry.isWild()) {
                            t.desc.add(tr("gui.minopp.cards.count_wild", c, def));
                        } else {
                            t.desc.add(
                                tr(
                                    "gui.minopp.cards.count_color",
                                    c,
                                    c * 4 * Math.max(1, entry.numbers().length),
                                    def));
                        }
                        String linked = ruleNames(TableConfig.rulesForCard(entry.typeId()));
                        if (!linked.isEmpty()) t.desc.add(tr("gui.minopp.cards.enables", linked));
                        String others = sharedWith(pack, faces);
                        if (!others.isEmpty()) t.desc.add(tr("gui.minopp.cards.shared", others));
                        t.controls.add(tr("gui.minopp.controls.card_left"));
                        t.controls.add(tr("gui.minopp.controls.card_right"));
                        t.controls.add(tr("gui.minopp.controls.card_ctrl"));
                        list.add(t);
                    }
                    list = filter(list, packName);
                    if (list.isEmpty()) continue;
                    y = header(packName, y);
                    tiles.addAll(list);
                    y = grid(list, y, 34, 52, 6) + 2;
                }
            }
            case TABLE -> {
                String name = tr("gui.minopp.rule_category.table");
                List<Tile> list = filter(ruleTiles(GameRules.Category.TABLE), name);
                if (!list.isEmpty()) {
                    y = header(name, y);
                    tiles.addAll(list);
                    y = grid(list, y, 58, 62, 8) + 2;
                }
                if (query.isEmpty()) {
                    Tile info = new Tile("deckinfo", Kind.INFO, null);
                    info.title = tr("gui.minopp.table.deck_title");
                    tiles.add(info);
                    info.x = 0;
                    info.y = y;
                    info.w = viewW();
                    info.h = 40;
                }
            }
            case STYLE -> {
                List<Tile> list = new ArrayList<>();
                for (ThemeManager.ThemeInfo theme : ThemeManager.INSTANCE.getThemes()) {
                    Tile t = new Tile("theme:" + theme.id(), Kind.THEME, theme);
                    String nameKey = "gui.minopp.theme." + theme.id();
                    t.title = net.minecraft.util.StatCollector.canTranslate(nameKey) ? tr(nameKey) : theme.name();
                    t.desc.add(tr("gui.minopp.style.desc"));
                    t.controls.add(tr("gui.minopp.controls.theme"));
                    list.add(t);
                }
                place(list, y, 112, 72, 8);
            }
        }
    }

    private void place(List<Tile> list, int y, int tileW, int tileH, int gap) {
        list = filter(list, "");
        tiles.addAll(list);
        grid(list, y, tileW, tileH, gap);
    }

    private List<Tile> filter(List<Tile> list, String groupName) {
        if (query.isEmpty()) return list;
        if (!groupName.isEmpty() && cachedMatch("group:" + groupName, groupName)) return list;
        List<Tile> out = new ArrayList<>();
        for (Tile t : list) {
            StringBuilder sb = new StringBuilder(t.title).append('\n')
                .append(t.searchExtra);
            for (String d : t.desc) sb.append('\n')
                .append(d);
            if (cachedMatch(t.id, sb.toString())) out.add(t);
        }
        return out;
    }

    private boolean cachedMatch(String id, String text) {
        Boolean b = matchCache.get(id);
        if (b == null) {
            b = SearchMatch.matches(text, query);
            matchCache.put(id, b);
        }
        return b;
    }

    private int effectiveCount(CardPack.Entry entry) {
        int c = 0;
        for (CardFace f : TableConfig.entryFaces(entry)) c = Math.max(c, effective.getOrDefault(f, 0));
        return c;
    }

    private static String ruleNames(String[] ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            GameRules.Rule r = GameRules.get(id);
            if (r == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(tr(r.langKey()));
        }
        return sb.toString();
    }

    private static Map<CardFace, Set<CardPack>> facePacks;

    private static String sharedWith(CardPack owner, List<CardFace> faces) {
        if (facePacks == null) {
            facePacks = new HashMap<>();
            for (CardPack pack : CardPack.all()) for (CardPack.Entry e : pack.entries)
                for (CardFace f : TableConfig.entryFaces(e)) facePacks.computeIfAbsent(f, k -> new LinkedHashSet<>())
                    .add(pack);
        }
        Set<String> names = new LinkedHashSet<>();
        for (CardFace f : faces)
            for (CardPack pack : facePacks.getOrDefault(f, Set.of())) if (pack != owner) names.add(tr(pack.langKey()));
        return String.join(", ", names);
    }

    private List<Tile> ruleTiles(GameRules.Category cat) {
        List<Tile> list = new ArrayList<>();
        for (GameRules.Rule r : GameRules.byCategory(cat)) {
            Tile t = new Tile("rule:" + r.id(), Kind.RULE, r);
            t.title = tr(r.langKey());
            t.desc.add(tr(r.langKey() + ".desc"));
            String needs = ruleNames(TableConfig.requiredRules(r.id()));
            if (!needs.isEmpty()) t.desc.add(tr("gui.minopp.rule.needs", needs));
            if (r.bool()) {
                t.controls.add(tr("gui.minopp.controls.rule_toggle"));
            } else {
                int[] presets = GameRules.presets(r.id());
                if (presets != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int p : presets) {
                        if (sb.length() > 0) sb.append(" / ");
                        sb.append(
                            p == 0 && r.id()
                                .equals(GameRules.SCORE_TARGET) ? tr("gui.minopp.rule.scoreTarget.single") : p);
                    }
                    t.desc.add(tr("gui.minopp.rule.presets", sb.toString()));
                    t.controls.add(tr("gui.minopp.controls.rule_preset"));
                } else {
                    t.desc.add(tr("gui.minopp.rule.range", r.min(), r.max()));
                    t.controls.add(tr("gui.minopp.controls.rule_int"));
                }
                t.controls.add(tr("gui.minopp.controls.rule_input"));
            }
            list.add(t);
        }
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

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        BlockEntityMinoTable te = table();
        if (te == null) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (previewTable != null && System.getProperty("minopp.uiPreviewTab") == null
            && System.currentTimeMillis() - previewSwitch > 6000) {
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
        if (lastMouseX != Integer.MIN_VALUE && (mouseX != lastMouseX || mouseY != lastMouseY)) {
            navId = null;
            navTabs = false;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hovered = null;
        mouseTile = null;
        boolean inView = mouseX >= viewX() && mouseX < viewX() + viewW()
            && mouseY >= viewY()
            && mouseY < viewY() + viewH();
        UiDraw.scissor(viewX() - 5, viewY() - 4, viewW() + 10, viewH() + 4);
        GL11.glPushMatrix();
        GL11.glTranslatef(viewX(), viewY() - offset, 0);
        Tile editing = null;
        for (Tile t : tiles) {
            if (t.id.equals(editingId)) editing = t;
            int sx = viewX() + t.x, sy = (int) (viewY() + t.y - offset);
            if (sy + t.h < viewY() - 10 || sy > viewY() + viewH() + 10) continue;
            boolean mouseOver = inView && !scroll.isDragging()
                && t.kind != Kind.HEADER
                && mouseX >= sx
                && mouseX < sx + t.w
                && mouseY >= sy
                && mouseY < sy + t.h;
            if (mouseOver) mouseTile = t;
            boolean hover = navId != null ? t.id.equals(navId) : mouseOver;
            if (hover) hovered = t;
            float a = hoverAnim.getOrDefault(t.id, 0f);
            a += ((hover ? 1f : 0f) - a) * Math.min(1, dt * 16);
            hoverAnim.put(t.id, a);
            drawTile(te, t, a);
        }
        GL11.glPopMatrix();
        UiDraw.endScissor();
        if (editingId != null) {
            if (editing == null || !editable()) {
                closeEditor(editing != null);
            } else {
                int sy = (int) (viewY() + editing.y - offset);
                numberField.xPosition = viewX() + editing.x + editing.w / 2 - 16;
                numberField.yPosition = sy + editing.h - (editing.kind == Kind.CARD ? 12 : 25);
                if (numberField.yPosition > viewY() - 8 && numberField.yPosition < viewY() + viewH()) {
                    GL11.glPushMatrix();
                    GL11.glTranslatef(0, 0, 120);
                    numberField.drawTextBox();
                    GL11.glPopMatrix();
                }
            }
        }
        int barX = contentX + contentW - 9;
        boolean barHover = mouseX >= barX - 3 && mouseX <= barX + 7 && mouseY >= viewY() && mouseY <= viewY() + viewH();
        scroll.drawBar(barX, viewY(), viewH(), barHover);

        if (hovered != infoTile && hovered != null) {
            infoTile = hovered;
            hoverStart = System.currentTimeMillis();
        }
        if (showInfo) drawInfoPanel(te);
        drawBottomBar(mouseX, mouseY);

        if (hovered != null && (navId == null || !showInfo) && System.currentTimeMillis() - hoverStart > 350) {
            List<String> lines = new ArrayList<>();
            lines.add(hovered.title);
            for (String d : hovered.desc)
                for (String l : fontRendererObj.listFormattedStringToWidth(d, 200)) lines.add(l);
            for (String c : hovered.controls) lines.add(EnumChatFormatting.GRAY + c);
            int tx = mouseX, ty = mouseY;
            if (navId != null) {
                tx = viewX() + hovered.x + hovered.w;
                ty = (int) (viewY() + hovered.y - offset);
            }
            UiDraw.tooltip(fontRendererObj, lines, tx, ty, width, height);
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
        keyQ = keyCap("Q", contentX, 39, mouseX, mouseY);
        keyE = keyCap("E", tabX(Tab.values().length), 39, mouseX, mouseY);
        for (Tab t : Tab.values()) {
            int x = tabX(t.ordinal()), y = 32;
            boolean sel = t == tab;
            boolean hover = mouseX >= x && mouseX < x + tw && mouseY >= y && mouseY < y + 28;
            if (sel && navTabs) {
                float pulse = 0.6f + 0.4f * (float) Math.sin(System.currentTimeMillis() % 100000L / 160.0);
                UiDraw.roundRect(
                    x - 4,
                    y - 4,
                    x + tw + 4,
                    y + 32,
                    8,
                    ((int) (pulse * 220) << 24) | (UiDraw.GLOW & 0xFFFFFF));
            }
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

    private int[] keyCap(String key, int x, int y, int mouseX, int mouseY) {
        int w = 11, h = 13;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        UiDraw.roundFrame(
            x,
            y,
            x + w,
            y + h,
            2,
            1,
            hover ? UiDraw.GLOW : 0xFF8A8A8A,
            hover ? 0xFF5A5A5A : 0xFF3A3A3A,
            0xFF2A2A2A);
        fontRendererObj.drawString(key, x + 3, y + 3, hover ? 0xFFFFFFFF : 0xFFE0E0E0);
        GL11.glColor4f(1, 1, 1, 1);
        return new int[] { x, y, w, h };
    }

    private static boolean inside(int[] r, int mx, int my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
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
                int iconY = r.bool() ? y1 + 2 + (t.h - 16 - 32) / 2 : y1 + 3;
                UiDraw.icon(r.id(), (x1 + x2) / 2f - 16, iconY, 32);
                UiDraw.fittedCentered(fontRendererObj, t.title, (x1 + x2) / 2f, y2 - 12, t.w - 6, 0xFFFFFFFF);
                if (r.bool()) {
                    checked = v != 0;
                    dim = v == 0;
                } else {
                    if (v == 0 && r.id()
                        .equals(GameRules.DECK_COPIES)) count = "A";
                    else if (v == 0 && r.id()
                        .equals(GameRules.SCORE_TARGET)) count = tr("gui.minopp.rule.scoreTarget.single");
                    else count = String.valueOf(v);
                }
                locked = !editable;
            }
            case CARD -> {
                Object[] p = (Object[]) t.payload;
                CardPack pack = (CardPack) p[0];
                CardPack.Entry entry = (CardPack.Entry) p[1];
                int c = effectiveCount(entry);
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
        if (four) UiDraw.image("badge_four", x1 + 2, y1 + 2, 10, 10);
        if (count != null && !t.id.equals(editingId)) {
            int pillY = t.kind == Kind.CARD ? y2 - 11 : y2 - 24;
            valuePill(count, (x1 + x2) / 2, pillY);
        }
        if (locked) UiDraw.image("badge_lock", x1 + 2, four ? y1 + 12 : y1 + 2, 10, 10);
    }

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

    private static void drawFan(List<CardFace> faces, String theme, float cx, float bottom, float cardW) {
        float cardH = cardW * 1.5625f;
        int n = faces.size();
        for (int i = 0; i < n; i++) {
            float angle = fanAngle(i, n);
            GL11.glPushMatrix();
            GL11.glTranslatef(cx, bottom, 0);
            GL11.glRotatef(angle, 0, 0, 1);
            CardRenderer.drawFace2D(faces.get(i), theme, -cardW / 2, -cardH, cardW, cardH);
            GL11.glPopMatrix();
        }
    }

    private static float fanAngle(int i, int n) {
        return n == 1 ? 0 : -24 + 48f * i / (n - 1);
    }

    private static void drawFanCentered(List<CardFace> faces, String theme, float cx, float top, float boxH,
        float cardW) {
        cardW = Math.min(cardW, (boxH - 12) / 1.5625f / 1.1f);
        float cardH = cardW * 1.5625f;
        int n = faces.size();
        float minY = 0, maxY = 0;
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(fanAngle(i, n));
            for (float[] c : new float[][] { { -cardW / 2, -cardH }, { cardW / 2, -cardH }, { -cardW / 2, 0 },
                { cardW / 2, 0 } }) {
                float y = (float) (c[0] * Math.sin(a) + c[1] * Math.cos(a));
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        drawFan(faces, theme, cx, top + boxH / 2f - (minY + maxY) / 2f, cardW);
    }

    private void drawInfoPanel(BlockEntityMinoTable te) {
        int x1 = infoX, y1 = contentY, x2 = infoX + infoW, y2 = contentY + contentH;
        UiDraw.panel(x1, y1, x2, y2);
        Tile t = infoTile;
        List<String> texts = new ArrayList<>();
        List<Integer> textColors = new ArrayList<>();
        if (t == null) {
            texts.add(describePacks(te.config));
            textColors.add(0xFFFFFFFF);
            texts.add(tr("gui.minopp.design.summary", te.config.countDeckSize(), te.seatCount()));
            textColors.add(0xFFFFFFFF);
        } else {
            for (String d : t.desc) {
                texts.add(d);
                textColors.add(0xFFFFFFFF);
            }
            for (String c : t.controls) {
                texts.add(c);
                textColors.add(0xFFBBBBBB);
            }
        }
        int py = y1 + 8, ph = Math.min(90, (y2 - y1) / 2);
        float textScale = 1f;
        int controlStart = t == null ? texts.size() : t.desc.size();
        int needed = wrappedHeight(texts, controlStart, infoW - 16, 1f);
        int avail = y2 - 6 - (py + ph + 32);
        if (needed > avail) {
            ph = Math.max(36, ph - (needed - avail));
            avail = y2 - 6 - (py + ph + 32);
            if (needed > avail) textScale = 0.75f;
            if (t != null && t.kind == Kind.RULE) ph = Math.max(ph, 40);
        }
        UiDraw.roundRect(x1 + 6, py, x2 - 6, py + ph, 5, 0x50000000);
        float cx = (x1 + x2) / 2f;
        if (t == null) {
            drawFanCentered(packPreview(te.config), ThemeManager.INSTANCE.selected(), cx, py, ph, 30);
        } else {
            switch (t.kind) {
                case RULE -> {
                    float size = Math.min(64, ph - 8);
                    UiDraw.icon(((GameRules.Rule) t.payload).id(), cx - size / 2f, py + (ph - size) / 2f, size);
                }
                case CARD -> {
                    CardPack.Entry entry = (CardPack.Entry) ((Object[]) t.payload)[1];
                    List<CardFace> faces = new ArrayList<>();
                    for (int i = 0; i < (entry.isWild() ? 1 : 4); i++) faces.add(sampleFace(entry, i));
                    drawFanCentered(faces, ThemeManager.INSTANCE.selected(), cx, py, ph, 34);
                }
                case PACK -> drawFanCentered(
                    packFaces((CardPack) t.payload),
                    ThemeManager.INSTANCE.selected(),
                    cx,
                    py,
                    ph,
                    32);
                case THEME -> {
                    String id = ((ThemeManager.ThemeInfo) t.payload).id();
                    drawFanCentered(
                        List.of(
                            new CardFace("number", CardColor.RED, 5),
                            new CardFace("number", CardColor.BLUE, 3),
                            new CardFace("skip", CardColor.GREEN, -1),
                            new CardFace("wild", CardColor.WILD, -1)),
                        id,
                        cx - 8,
                        py,
                        ph,
                        30);
                    CardRenderer.drawBack2D(id, false, x2 - 40, py + 8, 28, 44);
                }
                case DESIGN -> {
                    DesignStore.Design d = DesignStore.get((Integer) t.payload);
                    if (d != null)
                        drawFanCentered(packPreview(d.config), ThemeManager.INSTANCE.selected(), cx, py, ph, 30);
                    else UiDraw.image("plus", cx - 24, py + ph / 2f - 24, 48, 48);
                }
                default -> {}
            }
        }
        int hy = py + ph + 8;
        UiDraw.roundRect(x1 + 4, hy, x2 - 4, hy + 18, 4, 0xE0180404, 0xE0281010);
        String title = t == null ? tr("gui.minopp.settings.current") : t.title;
        UiDraw.fittedCentered(fontRendererObj, title, cx, hy + 5, infoW - 14, UiDraw.TITLE_YELLOW);
        float ty = hy + 24;
        float lineH = 10 * textScale;
        int wrapW = (int) ((infoW - 16) / textScale);
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            float fit = i >= controlStart ? fitScale(text, wrapW) : 0;
            if (fit > 0) {
                if (ty + lineH > y2 - 4) break;
                UiDraw.scaledCentered(
                    fontRendererObj,
                    text,
                    cx,
                    ty + lineH * (1 - fit) / 2f,
                    textScale * fit,
                    textColors.get(i));
                ty += lineH;
            } else {
                for (Object o : fontRendererObj.listFormattedStringToWidth(text, wrapW)) {
                    if (ty + lineH > y2 - 4) break;
                    UiDraw.scaledCentered(fontRendererObj, (String) o, cx, ty, textScale, textColors.get(i));
                    ty += lineH;
                }
            }
            ty += 3 * textScale;
        }
    }

    // short control hints are shrunk onto one line instead of wrapping mid-phrase (CJK has no spaces to break on)
    private float fitScale(String text, int width) {
        int w = fontRendererObj.getStringWidth(text);
        if (w <= width) return 1f;
        float s = width / (float) w;
        return s >= 0.62f ? s : 0;
    }

    private int wrappedHeight(List<String> texts, int controlStart, int width, float scale) {
        int lines = 0;
        int w = (int) (width / scale);
        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i);
            lines += i >= controlStart && fitScale(t, w) > 0 ? 1
                : fontRendererObj.listFormattedStringToWidth(t, w)
                    .size();
        }
        return (int) Math.ceil((lines * 10 + texts.size() * 3) * scale);
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
        int searchRight = search.xPosition + search.width + 8;
        String hint = tr(tab == Tab.DESIGNS ? "gui.minopp.settings.hint_designs" : "gui.minopp.settings.hint");
        int hw = UiDraw.pillWidth(fontRendererObj, "Q/E", hint);
        if (x - hw > searchRight) {
            UiDraw.pill(fontRendererObj, x - hw, y, "Q/E", hint, false);
            x -= hw + 6;
            String nav = tr("gui.minopp.settings.hint_nav");
            int nw = UiDraw.pillWidth(fontRendererObj, "WASD", nav);
            if (x - nw > searchRight) UiDraw.pill(fontRendererObj, x - nw, y, "WASD", nav, false);
        }
        search.drawTextBox();
        if (search.getText()
            .isEmpty() && !search.isFocused()) {
            fontRendererObj
                .drawString(tr("gui.minopp.settings.search"), search.xPosition + 4, search.yPosition + 3, 0xFF808080);
        }
        GL11.glColor4f(1, 1, 1, 1);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (editingId != null) {
            if (mouseX >= numberField.xPosition && mouseX < numberField.xPosition + numberField.width
                && mouseY >= numberField.yPosition
                && mouseY < numberField.yPosition + numberField.height) {
                numberField.mouseClicked(mouseX, mouseY, button);
                return;
            }
            closeEditor(true);
        }
        search.mouseClicked(mouseX, mouseY, button);
        if (search.isFocused()) {
            if (button == 1) {
                search.setText("");
                updateQuery();
            }
            return;
        }
        if (inside(keyQ, mouseX, mouseY)) {
            switchTab(Tab.values()[(tab.ordinal() + Tab.values().length - 1) % Tab.values().length]);
            return;
        }
        if (inside(keyE, mouseX, mouseY)) {
            switchTab(Tab.values()[(tab.ordinal() + 1) % Tab.values().length]);
            return;
        }
        for (Tab t : Tab.values()) {
            int x = tabX(t.ordinal());
            if (mouseX >= x && mouseX < x + tabWidth() && mouseY >= 32 && mouseY < 60) {
                switchTab(t);
                return;
            }
        }
        if (inside(pillBack, mouseX, mouseY)) {
            mc.displayGuiScreen(parent);
            return;
        }
        SmoothScroll scroll = scrolls.get(tab);
        if (scroll.mousePressed(mouseX, mouseY, contentX + contentW - 9, viewY(), viewH())) return;
        if (mouseTile != null) {
            navId = null;
            navTabs = false;
            onTileClicked(mouseTile, button);
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
        if (editingId != null) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                closeEditor(true);
            } else if (key == Keyboard.KEY_ESCAPE) {
                closeEditor(false);
            } else if (Character.isDigit(c) || c < 32 || key == Keyboard.KEY_DELETE) {
                numberField.textboxKeyTyped(c, key);
                String digits = numberField.getText()
                    .replaceAll("[^0-9]", "");
                if (!digits.equals(numberField.getText())) numberField.setText(digits);
            }
            return;
        }
        if (search.isFocused()) {
            if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN
                || key == Keyboard.KEY_NUMPADENTER
                || key == Keyboard.KEY_TAB) {
                search.setFocused(false);
            } else if (search.textboxKeyTyped(c, key)) {
                updateQuery();
            }
            return;
        }
        Tile focus = navId != null ? hovered : null;
        switch (key) {
            case Keyboard.KEY_Q -> switchTab(
                Tab.values()[(tab.ordinal() + Tab.values().length - 1) % Tab.values().length]);
            case Keyboard.KEY_E -> switchTab(Tab.values()[(tab.ordinal() + 1) % Tab.values().length]);
            case Keyboard.KEY_W, Keyboard.KEY_UP -> navigate(0, -1);
            case Keyboard.KEY_S, Keyboard.KEY_DOWN -> navigate(0, 1);
            case Keyboard.KEY_A, Keyboard.KEY_LEFT -> navigate(-1, 0);
            case Keyboard.KEY_D, Keyboard.KEY_RIGHT -> navigate(1, 0);
            case Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER, Keyboard.KEY_SPACE -> {
                if (navTabs) navigate(0, 1);
                else if (focus != null) onTileClicked(focus, 0);
            }
            case Keyboard.KEY_R -> {
                if (focus != null) onTileClicked(focus, 1);
            }
            case Keyboard.KEY_SLASH -> search.setFocused(true);
            case Keyboard.KEY_F -> {
                if (isCtrlKeyDown()) search.setFocused(true);
            }
            case Keyboard.KEY_DELETE, Keyboard.KEY_X -> {
                Tile t = focus != null ? focus : mouseTile;
                if (t != null && t.kind == Kind.DESIGN) DesignStore.delete((Integer) t.payload);
            }
            case Keyboard.KEY_ESCAPE -> mc.displayGuiScreen(parent);
            default -> super.keyTyped(c, key);
        }
    }

    private void updateQuery() {
        String q = search.getText()
            .trim();
        if (q.equals(query)) return;
        query = q;
        matchCache.clear();
        navId = null;
        for (SmoothScroll sc : scrolls.values()) sc.scrollTo(0);
    }

    private void navigate(int dx, int dy) {
        List<Tile> candidates = new ArrayList<>();
        for (Tile t : tiles) if (t.kind != Kind.HEADER && t.kind != Kind.INFO) candidates.add(t);
        Tile cur = null;
        for (Tile t : candidates) if (t.id.equals(navId)) cur = t;
        if (navTabs || cur == null) {
            if (navTabs && dx != 0) {
                switchTab(Tab.values()[(tab.ordinal() + Tab.values().length + dx) % Tab.values().length]);
                navTabs = true;
            } else if (dy < 0) {
                navTabs = true;
                navId = null;
            } else if (!candidates.isEmpty()) {
                navTabs = false;
                focusTile(candidates.get(0));
            }
            return;
        }
        float cx = cur.x + cur.w / 2f, cy = cur.y + cur.h / 2f;
        Tile best = null;
        float bestScore = Float.MAX_VALUE;
        for (Tile t : candidates) {
            if (t == cur) continue;
            float ddx = t.x + t.w / 2f - cx, ddy = t.y + t.h / 2f - cy;
            float score;
            if (dx != 0) {
                if (ddx * dx <= 0 || Math.abs(ddy) > cur.h / 2f) continue;
                score = Math.abs(ddx);
            } else {
                if (ddy * dy < cur.h / 2f) continue;
                score = Math.abs(ddy) + 2 * Math.abs(ddx);
            }
            if (score < bestScore) {
                bestScore = score;
                best = t;
            }
        }
        if (best != null) {
            focusTile(best);
        } else if (dy < 0) {
            navTabs = true;
            navId = null;
        }
    }

    private void focusTile(Tile t) {
        navId = t.id;
        SmoothScroll scroll = scrolls.get(tab);
        float off = scroll.offset();
        if (t.y - 22 < off) scroll.scrollTo(Math.max(0, t.y - 22));
        else if (t.y + t.h + 8 > off + viewH()) scroll.scrollTo(t.y + t.h + 8 - viewH());
        mc.getSoundHandler()
            .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.8f));
    }

    private void openEditor(Tile t, int value) {
        editingId = t.id;
        numberField = new GuiTextField(fontRendererObj, 0, -100, 32, 12);
        numberField.setMaxStringLength(5);
        numberField.setText(String.valueOf(value));
        numberField.setFocused(true);
        numberField.setCursorPositionEnd();
        numberField.setSelectionPos(0);
        click();
    }

    private void closeEditor(boolean commit) {
        String id = editingId;
        GuiTextField field = numberField;
        editingId = null;
        numberField = null;
        if (!commit || id == null
            || field == null
            || field.getText()
                .isEmpty()
            || !editable()) return;
        int v;
        try {
            v = Integer.parseInt(field.getText());
        } catch (NumberFormatException e) {
            return;
        }
        for (Tile t : tiles) if (t.id.equals(id)) {
            setValue(t, v);
            click();
            return;
        }
    }

    private void setValue(Tile t, int v) {
        BlockEntityMinoTable te = table();
        if (te == null) return;
        TableConfig cfg = te.config;
        if (t.kind == Kind.CARD) {
            Object[] p = (Object[]) t.payload;
            String key = TableConfig.countKey((CardPack) p[0], (CardPack.Entry) p[1]);
            int nv = v < 0 ? -1 : Math.min(64, v);
            cfg.setCount(key, nv);
            if (previewTable == null) C2SSeatControlPacket.Client.sendCountC2S(gamePos, key, nv);
        } else if (t.kind == Kind.RULE) {
            GameRules.Rule r = (GameRules.Rule) t.payload;
            int nv = r.clamp(v);
            if (r.id()
                .equals(GameRules.SCORE_TARGET) && nv > 0) nv = Math.max(50, nv);
            cfg.set(r.id(), nv);
            if (previewTable == null) C2SSeatControlPacket.Client.sendRuleC2S(gamePos, r.id(), nv);
        }
    }

    private void switchTab(Tab t) {
        if (t == tab) return;
        if (editingId != null) closeEditor(true);
        tab = t;
        infoTile = null;
        hovered = null;
        navId = null;
        mc.getSoundHandler()
            .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.2f));
    }

    private void click() {
        mc.getSoundHandler()
            .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0f));
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
                } else if (shift && button == 1) {
                    openEditor(t, v);
                    return;
                } else if (ctrl) {
                    nv = r.defaultValue();
                } else if (shift) {
                    nv = r.min();
                } else if (GameRules.presets(r.id()) != null) {
                    nv = GameRules.stepPreset(r.id(), v, button == 1 ? 1 : -1);
                } else {
                    nv = r.clamp(v + (button == 1 ? 1 : -1));
                }
                cfg.set(r.id(), nv);
                if (previewTable == null) C2SSeatControlPacket.Client.sendRuleC2S(gamePos, r.id(), nv);
            }
            case CARD -> {
                Object[] p = (Object[]) t.payload;
                CardPack pack = (CardPack) p[0];
                CardPack.Entry entry = (CardPack.Entry) p[1];
                String key = TableConfig.countKey(pack, entry);
                int c = effectiveCount(entry);
                int nv;
                if (shift && button == 1) {
                    openEditor(t, c);
                    return;
                } else if (ctrl) {
                    nv = -1;
                } else if (shift) {
                    nv = 0;
                } else {
                    nv = Math.max(0, Math.min(64, c + (button == 1 ? 1 : -1)));
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
