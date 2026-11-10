package com.hfstudio.minopp.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.client.gui.GuiScreen;

import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.DecisionTypes;
import com.hfstudio.minopp.game.PendingDecision;
import com.hfstudio.minopp.util.BlockPos;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class DecisionScreens {

    private static final Map<String, Factory> FACTORIES = new HashMap<>();

    private DecisionScreens() {}

    @FunctionalInterface
    public interface Factory {

        GuiScreen create(BlockPos table, CardPlayer player, PendingDecision decision);
    }

    public static void register(String typeId, Factory factory) {
        DecisionTypes.get(typeId);
        Objects.requireNonNull(factory, "factory");
        if (FACTORIES.putIfAbsent(typeId, factory) != null) {
            throw new IllegalArgumentException("Decision screen already registered: " + typeId);
        }
    }

    public static GuiScreen create(BlockPos table, CardPlayer player, PendingDecision decision) {
        Factory factory = FACTORIES.get(decision.typeId);
        return factory == null ? new DecisionScreen(table, player, decision) : factory.create(table, player, decision);
    }
}
