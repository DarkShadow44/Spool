package com.gamma.spool.util.distancemk2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class Notifier {

    public static void playerLeftWorld(World world) {
        notifyAllInWorld(world);
    }

    public static void playerEnteredWorld(World world) {
        notifyAllInWorld(world);
    }

    public static void playerChangedChunks(World world) {
        notifyAllInWorld(world);
    }

    public static void chunkForceLoading(World world, ChunkCoordIntPair location) {
        if (!DistanceUtil.enabled() || world.isRemote) return;
        ForcedChunkHandler.notifyOfChange(world, location);
    }

    public static void chunkStopForceLoading(World world, ChunkCoordIntPair location) {
        chunkForceLoading(world, location);
    }

    private static void notifyAllInWorld(World world) {
        if (!DistanceUtil.enabled() || world.isRemote) return;

        for (EntityPlayer player : world.playerEntities) {
            notifyPlayer(player);
        }
        notifyAllChunksInWorld0(world);
    }

    private static void notifyAllChunksInWorld(World world) {
        if (DistanceUtil.enabled() && !world.isRemote) notifyAllChunksInWorld0(world);
    }

    private static void notifyAllChunksInWorld0(World world) {
        if (!(world.getChunkProvider() instanceof ILoadedChunkAccessor accessor)) {
            throw new IllegalStateException(
                "World chunk provider of type " + world.getChunkProvider()
                    .getClass()
                    .getSimpleName() + " is not an instance of `ILoadedChunkAccessor`! This is a bug!");
        }
        for (Chunk chunk : accessor.spool$getAllLoadedChunks()) {
            notifyChunk(chunk);
        }
    }

    private static void notifyChunk(Chunk chunk) {
        ((IChunkExecutorInfoAccessor) chunk).spool$getChunkExecutorInfo()
            .notifyOfChange(chunk);
    }

    private static void notifyPlayer(EntityPlayer player) {
        ((IPlayerExecutorInfoAccessor) player).spool$getPlayerExecutorInfo()
            .notifyOfChange(player);
    }
}
