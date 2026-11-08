package com.hfstudio.minopp.network;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayerMP;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.util.Buf;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Id-dispatched raw payload channel; the 1.7.10 counterpart of the ServerPlatform/ClientPlatform packet helpers
 * of the 1.20.1 version. Packet handlers run on the main thread in 1.7.10.
 */
public final class MinoNetwork {

    private MinoNetwork() {}

    @FunctionalInterface
    public interface C2SPacketHandler {

        void handlePacket(EntityPlayerMP player, ByteBuf packet);
    }

    private static SimpleNetworkWrapper channel;
    private static final Map<String, C2SPacketHandler> SERVER_HANDLERS = new HashMap<>();
    private static final Map<String, Consumer<ByteBuf>> CLIENT_HANDLERS = new HashMap<>();

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(Mino.MOD_ID);
        channel.registerMessage(ServerHandler.class, C2SMessage.class, 0, Side.SERVER);
        channel.registerMessage(ClientHandler.class, S2CMessage.class, 1, Side.CLIENT);

        registerNetworkReceiver(C2SPlayCardPacket.ID, C2SPlayCardPacket::handleC2S);
        registerNetworkReceiver(C2SSeatControlPacket.ID, C2SSeatControlPacket::handleC2S);
        registerNetworkReceiver(C2SAutoPlayerConfigPacket.ID, C2SAutoPlayerConfigPacket::handleC2S);
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
            if (handler != null) {
                try {
                    handler.handlePacket(ctx.getServerHandler().playerEntity, message.data);
                } catch (Exception e) {
                    Mino.LOGGER.error("Error handling packet {}", message.id, e);
                }
            }
            return null;
        }
    }

    public static class ClientHandler implements IMessageHandler<S2CMessage, IMessage> {

        @Override
        public IMessage onMessage(S2CMessage message, MessageContext ctx) {
            Consumer<ByteBuf> handler = CLIENT_HANDLERS.get(message.id);
            if (handler != null) {
                try {
                    handler.accept(message.data);
                } catch (Exception e) {
                    Mino.LOGGER.error("Error handling packet {}", message.id, e);
                }
            }
            return null;
        }
    }
}
