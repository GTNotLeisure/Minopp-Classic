package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public interface TaskScheduler {

    void schedule(int ticks, Runnable task);

    public class Holder {

        public static final ServerTickScheduler INSTANCE = new ServerTickScheduler();
    }

    public class ServerTickScheduler implements TaskScheduler {

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
            List<Runnable> toRun = null;
            synchronized (tasks) {
                if (tasks.isEmpty()) return;
                int remaining = 0;
                for (ScheduledTask t : tasks) {
                    if (--t.ticksRemaining <= 0) {
                        if (toRun == null) toRun = new ArrayList<>();
                        toRun.add(t.task);
                    } else {
                        tasks.set(remaining++, t);
                    }
                }
                if (remaining < tasks.size()) tasks.subList(remaining, tasks.size())
                    .clear();
            }
            if (toRun != null) for (Runnable r : toRun) r.run();
        }
    }
}
