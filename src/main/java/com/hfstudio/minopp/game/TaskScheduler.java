package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public interface TaskScheduler {

    void schedule(int ticks, Runnable task);

    class Holder {

        public static final ServerTickScheduler INSTANCE = new ServerTickScheduler();
    }

    class ServerTickScheduler implements TaskScheduler {

        private final List<ScheduledTask> tasks = new ArrayList<>();

        private static class ScheduledTask {

            int ticksRemaining;
            final Runnable task;

            ScheduledTask(int ticks, Runnable task) {
                this.ticksRemaining = ticks;
                this.task = task;
            }
        }

        @Override
        public void schedule(int ticks, Runnable task) {
            synchronized (tasks) {
                tasks.add(new ScheduledTask(ticks, task));
            }
        }

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            List<Runnable> toRun = new ArrayList<>();
            synchronized (tasks) {
                if (tasks.isEmpty()) return;
                Iterator<ScheduledTask> it = tasks.iterator();
                while (it.hasNext()) {
                    ScheduledTask t = it.next();
                    if (--t.ticksRemaining <= 0) {
                        toRun.add(t.task);
                        it.remove();
                    }
                }
            }
            for (Runnable r : toRun) r.run();
        }
    }
}
