package com.hfstudio.minopp.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.hfstudio.minopp.game.CardFace;
import com.hfstudio.minopp.game.CardTypes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Optional card artwork providers. Layers are ordered from the frame to the front and cached per face and theme.
 */
@SideOnly(Side.CLIENT)
public class CardRenderRegistry {

    private static final Map<String, Function<CardFace, List<String>>> LAYERS = new HashMap<>();
    private static final Map<String, Function<CardFace, String>> SYMBOLS = new HashMap<>();

    private CardRenderRegistry() {}

    public static void register(String typeId, Function<CardFace, List<String>> layers) {
        Objects.requireNonNull(typeId, "typeId");
        if (!CardTypes.exists(typeId)) throw new IllegalArgumentException("Unknown card type: " + typeId);
        Objects.requireNonNull(layers, "layers");
        if (LAYERS.putIfAbsent(typeId, layers) != null) {
            throw new IllegalArgumentException("Card artwork already registered: " + typeId);
        }
        CardBatch.clearCache();
    }

    static List<String> layers(CardFace face) {
        Function<CardFace, List<String>> provider = LAYERS.get(face.typeId);
        return provider == null ? null : List.copyOf(provider.apply(face));
    }

    /**
     * Supplies the standalone symbol layer, including per-number artwork, for corners and table effects.
     */
    public static void registerSymbol(String typeId, Function<CardFace, String> symbol) {
        if (!CardTypes.exists(typeId)) throw new IllegalArgumentException("Unknown card type: " + typeId);
        Objects.requireNonNull(symbol, "symbol");
        if (SYMBOLS.putIfAbsent(typeId, symbol) != null) {
            throw new IllegalArgumentException("Card symbol already registered: " + typeId);
        }
        CardBatch.clearCache();
    }

    static String symbol(CardFace face) {
        Function<CardFace, String> provider = SYMBOLS.get(face.typeId);
        return provider == null ? null : Objects.requireNonNull(provider.apply(face), "symbol layer");
    }
}
