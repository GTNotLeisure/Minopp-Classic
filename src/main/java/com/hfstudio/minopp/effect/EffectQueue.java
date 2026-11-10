package com.hfstudio.minopp.effect;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import com.hfstudio.minopp.util.BlockPos;

public class EffectQueue {

    private final PriorityQueue<TimedEvent> queue = new PriorityQueue<>(Comparator.comparingLong(a -> a.time));
    private World world;

    public void clear() {
        synchronized (queue) {
            queue.clear();
            world = null;
        }
    }

    public void tick(World level) {
        long time = System.currentTimeMillis();
        synchronized (queue) {
            if (level == null || level != world) {
                queue.clear();
                world = level;
                return;
            }
            while (!queue.isEmpty() && queue.peek().time <= time) {
                queue.poll()
                    .summon(level);
            }
        }
    }

    public void addAll(List<EffectEvent> events, BlockPos origin, EntityPlayer self, boolean selfIsPartOfSourceGame) {
        if (self == null || self.worldObj == null) return;
        synchronized (queue) {
            if (world != self.worldObj) {
                queue.clear();
                world = self.worldObj;
            }
            long time = System.currentTimeMillis();
            for (EffectEvent event : events) {
                if (event.target()
                    .isEmpty()
                    || event.target()
                        .get()
                        .equals(
                            self.getGameProfile()
                                .getId())) {
                    queue.add(new TimedEvent(event, time, origin, selfIsPartOfSourceGame));
                }
            }
        }
    }

    private static class TimedEvent {

        private final EffectEvent event;
        private final long time;
        private final BlockPos origin;
        private final boolean selfIsPartOfSourceGame;

        public TimedEvent(EffectEvent incoming, long baseTime, BlockPos origin, boolean selfIsPartOfSourceGame) {
            this.event = incoming;
            this.time = baseTime + incoming.timeOffset();
            this.origin = origin;
            this.selfIsPartOfSourceGame = selfIsPartOfSourceGame;
        }

        public void summon(World level) {
            event.summonClient(level, origin, selfIsPartOfSourceGame);
        }
    }
}
