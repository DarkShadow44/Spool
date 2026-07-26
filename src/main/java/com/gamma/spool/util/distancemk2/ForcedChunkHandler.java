package com.gamma.spool.util.distancemk2;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeChunkManager;

import com.gamma.spool.config.DistanceThreadingConfig;
import com.google.common.collect.SetMultimap;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongStack;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public class ForcedChunkHandler {

    private static final int[][] OFFSETS = new int[][] { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 } };

    public static void notifyOfChange(World world, ChunkCoordIntPair location) {
        // Special checks for floodfilling forced chunks and synchronizing player executors on them.
        SetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> map = ForgeChunkManager.getPersistentChunksFor(world);

        if (!map.isEmpty()) {
            Set<ChunkCoordIntPair> keys = map.keySet();
            LongSet longKeys = keys.stream()
                .mapToLong(ccip -> ChunkCoordIntPair.chunkXZ2Int(ccip.chunkXPos, ccip.chunkZPos))
                .collect(LongArraySet::new, LongArraySet::add, LongArraySet::addAll);
            floodNearbyChunks(world, longKeys, location.chunkXPos, location.chunkXPos);
        }
    }

    private static void floodNearbyChunks(World world, LongSet chunks, int chunkX, int chunkZ) {

        List<EntityPlayer> playerList = world.playerEntities;
        Executor executor = null;

        LongStack stack = new LongArrayList();
        LongSet visited = new LongOpenHashSet();
        Set<EntityPlayer> nearbyPlayers = new ObjectOpenHashSet<>();
        Set<Chunk> queued = new ObjectArraySet<>();

        stack.push(ChunkCoordIntPair.chunkXZ2Int(chunkX, chunkZ));
        while (!stack.isEmpty()) {
            long chunkToVisit = stack.popLong();
            int x = undoChunkX(chunkToVisit);
            int z = undoChunkZ(chunkToVisit);
            if (visited.contains(ChunkCoordIntPair.chunkXZ2Int(x, z))) continue;
            for (int[] offset : OFFSETS) {
                int newX = x + offset[0];
                int newZ = z + offset[1];
                long hash = ChunkCoordIntPair.chunkXZ2Int(newX, newZ);
                if (chunks.contains(hash)) {
                    visited.add(hash);
                    stack.push(hash);
                    Chunk otherChunk = world.getChunkFromChunkCoords(newX, newZ);
                    if (executor == null) {
                        if (DistanceUtil.getExecutor(otherChunk) != null) {
                            executor = DistanceUtil.getExecutor(otherChunk);
                            for (Chunk chunk : queued) DistanceUtil.setExecutor(chunk, executor);
                            queued.clear();
                        } else queued.add(otherChunk);
                    } else DistanceUtil.setExecutor(otherChunk, executor);
                    for (EntityPlayer player : playerList) {
                        if (DistanceUtil.isChunkInsidePlayerRange(player, otherChunk)) {
                            nearbyPlayers.add(player);
                        }
                    }
                }
            }
        }

        // true if none of the chunks we just floodfilled had an executor.
        if (!queued.isEmpty()) {
            if (nearbyPlayers.isEmpty())
                // run on main thread
                executor = DistanceUtil.MAIN_THREAD;
            else
                // run on the executor of the first player we find
                executor = DistanceUtil.getExecutor(
                    queued.iterator()
                        .next());
            for (Chunk chunk : queued) DistanceUtil.setExecutor(chunk, executor);
        }
        final Executor finalExecutor = executor;

        if (DistanceThreadingConfig.streamParallelizationLevel > 1 && nearbyPlayers.size() > 1) {
            nearbyPlayers.parallelStream()
                .forEach(player -> DistanceUtil.setExecutor(player, finalExecutor));
        } else {
            for (EntityPlayer player : nearbyPlayers) {
                DistanceUtil.setExecutor(player, executor);
            }
        }
    }

    private static int undoChunkX(long chunkHash) {
        return (int) (chunkHash & 4294967295L);
    }

    private static int undoChunkZ(long chunkHash) {
        return (int) ((chunkHash >> 32) & 4294967295L);
    }
}
