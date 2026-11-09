package com.hfstudio.minopp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MathHelper;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.block.BlockMinoTable;
import com.hfstudio.minopp.game.ActionReport;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.Vec3d;

public class MinoCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "minopp";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/minopp <shout|force_discard|force_draw [count]|set_table_award|set_table_demo <true|false>|debug [start|hold|stop|turn|next|...]>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    private static void requireOp(ICommandSender sender) {
        if (!sender.canCommandSenderUseCommand(2, "minopp")) {
            throw new CommandException("commands.generic.permission");
        }
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) throw new WrongUsageException(getCommandUsage(sender));
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        switch (args[0]) {
            case "shout" -> {
                boolean success = Mino.onServerChatMessage("mino", player);
                if (!success) throw new CommandException("game.minopp.play.no_game");
            }
            case "force_discard" -> {
                requireOp(sender);
                withPlayerAndGame(player, (game, cardPlayer) -> {
                    ActionReport report = ActionReport.builder(game, cardPlayer);
                    if (cardPlayer.hand.isEmpty()) return;
                    game.doDiscardCard(cardPlayer, cardPlayer.hand.get(0), report);
                });
            }
            case "force_draw" -> {
                requireOp(sender);
                int count = args.length >= 2 ? parseIntBounded(sender, args[1], 1, 100) : 1;
                withPlayerAndGame(player, (game, cardPlayer) -> {
                    ActionReport report = ActionReport.builder(game, cardPlayer);
                    game.doDrawCard(cardPlayer, count, report);
                });
            }
            case "set_table_award" -> {
                requireOp(sender);
                ItemStack holdingItem = player.getHeldItem();
                if (holdingItem == null) {
                    throw new CommandException("Requirement: Hold an item");
                }
                BlockEntityMinoTable tableEntity = getTableUnder(player);
                tableEntity.award = holdingItem.copy();
                tableEntity.sync();
                func_152373_a(sender, this, "Table award set");
            }
            case "set_table_demo" -> {
                requireOp(sender);
                if (args.length < 2) throw new WrongUsageException(getCommandUsage(sender));
                BlockEntityMinoTable tableEntity = getTableUnder(player);
                tableEntity.demo = parseBoolean(sender, args[1]);
                tableEntity.sync();
                func_152373_a(sender, this, "Table demo set");
            }
            case "debug" -> {
                MinecraftServer server = MinecraftServer.getServer();
                boolean worldOwner = server.isSinglePlayer() && player.getCommandSenderName()
                    .equals(server.getServerOwner());
                if (!sender.canCommandSenderUseCommand(2, "minopp") && !worldOwner) {
                    throw new CommandException("commands.generic.permission");
                }
                debug(player, args.length >= 2 ? args[1] : "start");
            }
            default -> throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private static BlockEntityMinoTable nearestTable(EntityPlayerMP player) {
        UUID id = player.getGameProfile()
            .getId();
        BlockEntityMinoTable best = null;
        double bestDist = 12 * 12;
        for (Object o : player.worldObj.loadedTileEntityList) {
            if (!(o instanceof BlockEntityMinoTable table)) continue;
            if (table.seatOf(id) >= 0) return table;
            double dx = table.xCoord + 1.5 - player.posX, dz = table.zCoord + 1.5 - player.posZ;
            double dy = table.yCoord - player.posY;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = table;
            }
        }
        return best;
    }

    private static void debug(EntityPlayerMP player, String mode) {
        BlockEntityMinoTable table = nearestTable(player);
        if (table == null) throw new CommandException("game.minopp.debug.no_table");
        UUID id = player.getGameProfile()
            .getId();
        switch (mode) {
            case "start" -> {
                CardPlayer me = new CardPlayer(player);
                if (table.game != null) table.destroyGame(me);
                if (table.seatOf(id) < 0) table.joinPlayerToTable(me, new Vec3d(player.posX, player.posY, player.posZ));
                if (table.seatOf(id) < 0) throw new CommandException("game.minopp.debug.no_seat");
                table.startGame(me, id);
                player.addChatComponentMessage(Chat.translatable("game.minopp.debug.started"));
            }
            case "hold" -> {
                if (table.game == null || !table.game.debug) throw new CommandException("game.minopp.debug.not_debug");
                table.game.debugHold = !table.game.debugHold;
                player.addChatComponentMessage(
                    Chat.translatable(
                        table.game.debugHold ? "game.minopp.debug.hold_on" : "game.minopp.debug.hold_off"));
            }
            case "stop" -> {
                if (table.game == null || !table.game.debug) throw new CommandException("game.minopp.debug.not_debug");
                table.destroyGame(new CardPlayer(player));
                player.addChatComponentMessage(Chat.translatable("game.minopp.debug.stopped"));
            }
            default -> {
                if (table.game == null || !table.game.debug) throw new CommandException("game.minopp.debug.not_debug");
                CardPlayer me = table.game.deAmputate(id);
                table.handleActionResult(table.game.debugAction(mode), me, player);
                return;
            }
        }
        table.sync();
    }

    private static BlockEntityMinoTable getTableUnder(EntityPlayerMP player) {
        int x = MathHelper.floor_double(player.posX);
        int y = MathHelper.floor_double(player.boundingBox.minY - 0.2);
        int z = MathHelper.floor_double(player.posZ);
        if (player.worldObj.getBlock(x, y, z) != Mino.BLOCK_MINO_TABLE) {
            throw new CommandException("Requirement: Stand on a table");
        }
        BlockPos corePos = BlockMinoTable.getCore(player.worldObj, x, y, z);
        BlockEntityMinoTable tableEntity = ItemHandCards.getTable(player.worldObj, corePos);
        if (tableEntity == null) throw new CommandException("Requirement: Stand on a table");
        return tableEntity;
    }

    private static void withPlayerAndGame(EntityPlayerMP player, BiConsumer<CardGame, CardPlayer> action) {
        BlockPos gamePos = ItemHandCards.getHandCardGamePos(player);
        if (gamePos == null) throw new CommandException(
            Chat.translatable("game.minopp.play.no_game")
                .getUnformattedText());
        BlockEntityMinoTable tableEntity = ItemHandCards.getTable(player.worldObj, gamePos);
        if (tableEntity == null || tableEntity.game == null) throw new CommandException("game.minopp.play.no_game");
        CardPlayer cardPlayer = tableEntity.game.deAmputate(ItemHandCards.getCardPlayer(player));
        if (cardPlayer == null) throw new CommandException("game.minopp.play.no_game");
        action.accept(tableEntity.game, cardPlayer);
        tableEntity.sync();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(
                args,
                "shout",
                "force_discard",
                "force_draw",
                "set_table_award",
                "set_table_demo",
                "debug");
        }
        if (args.length == 2 && args[0].equals("debug")) {
            List<String> modes = new ArrayList<>(CardGame.DEBUG_ACTIONS);
            modes.add(0, "start");
            return getListOfStringsMatchingLastWord(args, modes.toArray(new String[0]));
        }
        if (args.length == 2 && args[0].equals("set_table_demo")) {
            return getListOfStringsMatchingLastWord(args, "true", "false");
        }
        return null;
    }

    @Override
    public int compareTo(Object o) {
        return getCommandName().compareTo(((CommandBase) o).getCommandName());
    }

    @SuppressWarnings("unused")
    private static final List<String> SUBCOMMANDS = Arrays
        .asList("shout", "force_discard", "force_draw", "set_table_award", "set_table_demo", "debug");
}
