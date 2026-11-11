package com.hfstudio.minopp.gui;

import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiScreen;

import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.util.BlockPos;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class DebugCardScreen extends HandOverviewScreen {

    public DebugCardScreen(GuiScreen parent, BlockPos tablePos, long gameId, UUID playerId, Consumer<Card> selection) {
        super(parent, tablePos, gameId, playerId, selection);
    }
}
