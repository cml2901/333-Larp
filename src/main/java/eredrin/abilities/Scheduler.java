package eredrin.abilities;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Простой планировщик отложенных задач (в тиках). */
@EventBusSubscriber(modid = EredrinAbilities.MODID)
public final class Scheduler {
    private static final class Task {
        int delay;
        final Runnable run;
        Task(int delay, Runnable run) { this.delay = delay; this.run = run; }
    }

    private static final List<Task> TASKS = new ArrayList<>();

    private Scheduler() {}

    public static void later(int ticks, Runnable run) {
        TASKS.add(new Task(Math.max(1, ticks), run));
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        List<Task> due = new ArrayList<>();
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (--t.delay <= 0) {
                due.add(t);
                it.remove();
            }
        }
        for (Task t : due) t.run.run();
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent event) {
        TASKS.clear();
    }
}
