package com.gamma.spool.util.distancemk2;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import com.gamma.spool.config.DistanceThreadingConfig;
import com.gamma.spool.config.ThreadsConfig;
import com.google.common.base.Throwables;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

public class DistanceUtil {

    static final Logger LOGGER = LogManager.getLogger("Spool-DistanceThreading");
    static final Executor MAIN_THREAD = Runnable::run;

    // this uses a fast pool, so no separate impl here.
    private static final BiMap<Integer, Executor> pool = HashBiMap.create();
    private static final BiMap<Executor, Integer> inversePool = pool.inverse();
    private static final Int2IntMap referenceMap = Int2IntMaps
        .synchronize(new Int2IntOpenHashMap(ThreadsConfig.distanceMaxThreads));

    static volatile boolean toThread = false;

    public static void enable() {
        toThread = true;
        LOGGER.info("Distance threading system enabled");
    }

    public static void disable() {
        toThread = false;
        LOGGER.info("Distance threading system disabled");
    }

    public static boolean enabled() {
        return toThread;
    }

    static Executor requestNewExecutor(EntityPlayer player) {
        int id = player.getEntityId();
        if (referenceMap.get(id) > 0) return pool.get(id);
        if (referenceMap.size() >= ThreadsConfig.distanceMaxThreads) {
            LOGGER.info("Executor id: {} doesn't exist yet, but the pool is full. The main thread will be used.", id);
            return MAIN_THREAD;
        }
        LOGGER.info("Executor id: {} doesn't exist yet, it will now be created", id);
        return pool.computeIfAbsent(id, SwitchableExecutor::new);
    }

    static void notifyExecutorReferenceRemoved(Executor executor) {
        if (executor == null || executor == MAIN_THREAD) return;
        if (!inversePool.containsKey(executor)) {
            LOGGER.warn("Executor reference removed, but doesn't exist in the pool, ignoring");
            return;
        }
        int id = inversePool.get(executor);
        int newNumReferences = referenceMap.compute(id, (_, oldValue) -> (oldValue == null ? 0 : oldValue) - 1);
        // LOGGER.info("Reference to executor id: {} removed, new reference count: {}", id, newNumReferences);
        if (newNumReferences <= 0) {
            LOGGER.info("Executor id: {} has no more references", id);
            pool.remove(id);
        }
    }

    static void notifyExecutorReferenceAdded(Executor executor) {
        if (executor == null || executor == MAIN_THREAD) return;
        if (!inversePool.containsKey(executor)) {
            LOGGER.error("Executor reference added, but doesn't exist in the pool. This is an error condition!");
            return;
        }
        int id = inversePool.get(executor);
        int newNumReferences = referenceMap.compute(id, (_, oldValue) -> (oldValue == null ? 0 : oldValue) + 1);
        // LOGGER.info("Reference to executor id: {} added, new reference count: {}", id, newNumReferences);
    }

    static int getDistanceLimit() {
        int limit = MinecraftServer.getServer()
            .getConfigurationManager()
            .getViewDistance() * 2;
        int configLimit = DistanceThreadingConfig.threadChunkDistance;
        if (limit > configLimit && configLimit != 0) throw new IllegalArgumentException(
            "View distance is too high for threadChunkDistance! " + limit + " > " + configLimit);
        else if (configLimit == 0) configLimit = limit;
        return configLimit;
    }

    static boolean isPlayerInsidePlayerRange(EntityPlayer player, EntityPlayer otherPlayer) {
        int limit = getDistanceLimit() + 1;
        if (otherPlayer.chunkCoordX < player.chunkCoordX + limit
            && otherPlayer.chunkCoordX > player.chunkCoordX - limit) {
            return otherPlayer.chunkCoordZ < player.chunkCoordZ + limit
                && otherPlayer.chunkCoordZ > player.chunkCoordZ - limit;
        }
        return false;
    }

    static boolean isChunkInsidePlayerRange(EntityPlayer player, Chunk chunk) {
        int limit = getDistanceLimit() + 1;
        if (chunk.xPosition < player.chunkCoordX + limit && chunk.xPosition > player.chunkCoordX - limit) {
            return chunk.zPosition < player.chunkCoordZ + limit && chunk.zPosition > player.chunkCoordZ - limit;
        }
        return false;
    }

    public static void waitForAll() {
        for (Executor executor : pool.values()) {
            if (!(executor instanceof SwitchableExecutor switchableExecutor)) continue;
            try {
                switchableExecutor.waitForFinish();
            } catch (InterruptedException | ExecutionException e) {
                Throwables.propagate(e);
            }
        }
    }

    public static Executor getExecutor(Chunk chunk) {
        return ((IExecutorAccessor) chunk).getExecutor();
    }

    public static Executor getExecutor(World world, int x, int z) {
        IChunkProvider provider = world.getChunkProvider();
        if (!provider.chunkExists(x, z)) return MAIN_THREAD;
        return ((IExecutorAccessor) provider.provideChunk(x, z)).getExecutor();
    }

    public static Executor getExecutor(Entity entity) {
        if (entity instanceof EntityPlayer) return ((IExecutorAccessor) entity).getExecutor();
        else return getExecutor(entity.worldObj, entity.chunkCoordX, entity.chunkCoordZ);
    }

    static void setExecutor(EntityPlayer player, Executor executor) {
        IPlayerExecutorInfoAccessor accessor = (IPlayerExecutorInfoAccessor) player;
        PlayerExecutorInfo playerExecutorInfo = accessor.spool$getPlayerExecutorInfo();
        DistanceUtil.notifyExecutorReferenceRemoved(playerExecutorInfo.executor);
        playerExecutorInfo.executor = executor;
        LOGGER.info("Player {} executor changed to ID: {}", player.getCommandSenderName(), inversePool.get(executor));
        DistanceUtil.notifyExecutorReferenceAdded(executor);
    }

    static void setExecutor(Chunk chunk, Executor executor) {
        IChunkExecutorInfoAccessor accessor = (IChunkExecutorInfoAccessor) chunk;
        ChunkExecutorInfo info = accessor.spool$getChunkExecutorInfo();
        DistanceUtil.notifyExecutorReferenceRemoved(info.executor);
        // set the other chunk's executor to this chunk's executor
        info.executor = executor;
        LOGGER.info(
            "Chunk ({},{}) executor changed to ID: {}",
            chunk.xPosition,
            chunk.zPosition,
            inversePool.get(executor));
        DistanceUtil.notifyExecutorReferenceAdded(executor);
    }

    static void setExecutor(ChunkExecutorInfo info, EntityPlayer player) {
        IPlayerExecutorInfoAccessor accessor = (IPlayerExecutorInfoAccessor) player;
        PlayerExecutorInfo playerExecutorInfo = accessor.spool$getPlayerExecutorInfo();
        DistanceUtil.notifyExecutorReferenceRemoved(info.executor);
        // set the other chunk's executor to this chunk's executor
        info.executor = playerExecutorInfo.executor;
        // LOGGER.info("Chunk ({},{}) executor changed to ID: {}", info.thisThing.xPosition, info.thisThing.zPosition,
        // inversePool.get(info.executor));
        DistanceUtil.notifyExecutorReferenceAdded(playerExecutorInfo.executor);
    }

    private record SwitchableExecutor(ExecutorService executor, Queue<Future<?>> futures) implements Executor {

        // lambda optimization, yay.
        private SwitchableExecutor(int unused) {
            this(Executors.newSingleThreadExecutor(), new ConcurrentLinkedQueue<>());
        }

        @Override
        public void execute(@NotNull Runnable command) {
            if (DistanceUtil.toThread) futures.add(executor.submit(command));
            else command.run();
        }

        public void waitForFinish() throws ExecutionException, InterruptedException {
            while (!futures.isEmpty()) {
                futures.poll()
                    .get();
            }
        }
    }
}
