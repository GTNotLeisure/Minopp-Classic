package com.hfstudio.minopp.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayerMP;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.util.Buf;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class MinoNetwork {

    private MinoNetwork() {}

    @FunctionalInterface
    public interface C2SPacketHandler {

        void handlePacket(EntityPlayerMP player, ByteBuf packet);
    }

    private static SimpleNetworkWrapper channel;

    /*
     * FML hands messages over on the netty thread. Game state, worlds and screens belong to the main threads, so every
     * message is queued and handled at the start of the next tick. The queues are capped so a flood of packets can
     * not pile up memory.
     */
    private static final int QUEUE_CAP = 4096;
    private static final Queue<Runnable> SERVER_QUEUE = new ConcurrentLinkedQueue<>();
    private static final Queue<Runnable> CLIENT_QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger SERVER_PENDING = new AtomicInteger(), CLIENT_PENDING = new AtomicInteger();

    private static void enqueue(Queue<Runnable> queue, AtomicInteger pending, Runnable task) {
        if (pending.incrementAndGet() > QUEUE_CAP) {
            pending.decrementAndGet();
            return;
        }
        queue.add(task);
    }

    private static void drain(Queue<Runnable> queue, AtomicInteger pending) {
        Runnable task;
        while ((task = queue.poll()) != null) {
            pending.decrementAndGet();
            task.run();
        }
    }

    /** Handles the client's queued messages; called from the client tick. */
    public static void runClientQueue() {
        drain(CLIENT_QUEUE, CLIENT_PENDING);
    }

    public static class ServerTicker {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.START) drain(SERVER_QUEUE, SERVER_PENDING);
        }
    }

    private static final Map<String, C2SPacketHandler> SERVER_HANDLERS = new HashMap<>();
    private static final Map<String, Consumer<ByteBuf>> CLIENT_HANDLERS = new HashMap<>();

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(Mino.MOD_ID);
        channel.registerMessage(ServerHandler.class, C2SMessage.class, 0, Side.SERVER);
        channel.registerMessage(ClientHandler.class, S2CMessage.class, 1, Side.CLIENT);

        registerNetworkReceiver(C2SPlayCardPacket.ID, C2SPlayCardPacket::handleC2S);
        registerNetworkReceiver(C2SSeatControlPacket.ID, C2SSeatControlPacket::handleC2S);
        registerNetworkReceiver(C2SAutoPlayerConfigPacket.ID, C2SAutoPlayerConfigPacket::handleC2S);
        registerNetworkReceiver(ThemeSyncPackets.C2S_ID, ThemeSyncPackets::handleC2S);
    }

    public static void registerNetworkReceiver(String id, C2SPacketHandler handler) {
        SERVER_HANDLERS.put(id, handler);
    }

    public static void registerClientReceiver(String id, Consumer<ByteBuf> handler) {
        CLIENT_HANDLERS.put(id, handler);
    }

    public static ByteBuf buffer() {
        return Unpooled.buffer();
    }

    public static void sendPacketToServer(String id, ByteBuf packet) {
        channel.sendToServer(new C2SMessage(id, packet));
    }

    public static void sendPacketToPlayer(EntityPlayerMP player, String id, ByteBuf packet) {
        channel.sendTo(new S2CMessage(id, packet), player);
    }

    public abstract static class RawMessage implements IMessage {

        String id;
        ByteBuf data;

        RawMessage() {}

        RawMessage(String id, ByteBuf data) {
            this.id = id;
            this.data = data;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            id = Buf.readString(buf);
            data = Unpooled.copiedBuffer(buf.readBytes(buf.readableBytes()));
        }

        @Override
        public void toBytes(ByteBuf buf) {
            Buf.writeString(buf, id);
            buf.writeBytes(data, data.readerIndex(), data.readableBytes());
        }
    }

    public static class C2SMessage extends RawMessage {

        public C2SMessage() {}

        C2SMessage(String id, ByteBuf data) {
            super(id, data);
        }
    }

    public static class S2CMessage extends RawMessage {

        public S2CMessage() {}

        S2CMessage(String id, ByteBuf data) {
            super(id, data);
        }
    }

    public static class ServerHandler implements IMessageHandler<C2SMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SMessage message, MessageContext ctx) {
            C2SPacketHandler handler = SERVER_HANDLERS.get(message.id);
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (handler == null || player == null) return null;
            enqueue(SERVER_QUEUE, SERVER_PENDING, () -> {
                if (player.isDead || player.playerNetServerHandler == null) return;
                try {
                    handler.handlePacket(player, message.data);
                } catch (Exception e) {
                    Mino.LOGGER
                        .warn("Bad {} packet from {}: {}", message.id, player.getCommandSenderName(), e.toString());
                }
            });
            return null;
        }
    }

    public static class ClientHandler implements IMessageHandler<S2CMessage, IMessage> {

        @Override
        public IMessage onMessage(S2CMessage message, MessageContext ctx) {
            Consumer<ByteBuf> handler = CLIENT_HANDLERS.get(message.id);
            if (handler == null) return null;
            enqueue(CLIENT_QUEUE, CLIENT_PENDING, () -> {
                try {
                    handler.accept(message.data);
                } catch (Exception e) {
                    Mino.LOGGER.error("Error handling packet {}", message.id, e);
                }
            });
            return null;
        }
    }
}
