package com.llamastudio.zoltraak;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Bo hen gio don gian (thay cho "delay" cua MythicMobs). Don vi: tick. */
public final class Scheduler {

    private static final class Task {
        int remaining;
        final Runnable action;

        Task(int remaining, Runnable action) {
            this.remaining = remaining;
            this.action = action;
        }
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();

    private Scheduler() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(Scheduler::onTick);
        NeoForge.EVENT_BUS.addListener(Scheduler::onStopping);
    }

    public static void later(int ticks, Runnable action) {
        PENDING.add(new Task(Math.max(1, ticks), action));
    }

    private static void onTick(ServerTickEvent.Post event) {
        if (!PENDING.isEmpty()) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task task = it.next();
            if (--task.remaining <= 0) {
                it.remove();
                try {
                    task.action.run();
                } catch (Exception e) {
                    Zoltraak.LOGGER.error("Zoltraak scheduled task failed", e);
                }
            }
        }
    }

    private static void onStopping(ServerStoppingEvent event) {
        TASKS.clear();
        PENDING.clear();
    }
}
