package com.gamma.spool.util.distancemk2;

import java.util.List;
import java.util.Optional;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.chunk.Chunk;

import com.gamma.spool.config.DistanceThreadingConfig;

public class ChunkExecutorInfo extends AbstractExecutorInfo<Chunk> {

    public ChunkExecutorInfo(Chunk thisChunk) {
        super(thisChunk);
    }

    @Override
    public void notifyOfChange(Chunk chunk) {
        recalculateExecutor();
    }

    @Override
    protected synchronized void recalculateExecutor() {
        // Rebuild based on all players in the server (optionally parallel)
        List<EntityPlayer> playerList = thisThing.worldObj.playerEntities;
        Optional<Entry> entry = Optional.empty();
        if (DistanceThreadingConfig.streamParallelizationLevel > 0 && playerList.size() > 1) {
            // find nearest player
            entry = playerList.parallelStream()
                .map(Entry::new)
                .reduce((a, b) -> a.distance < b.distance ? a : b);
        } else {
            for (EntityPlayer player : playerList) {
                Entry e = new Entry(player);
                if (entry.isEmpty() || e.distance < entry.get().distance) {
                    entry = Optional.of(e);
                }
            }
        }

        if (entry.isEmpty()) {
            // If there are no nearby players, set the executor to the main thread; Happens only when no players are in
            // this dimension.
            executor = DistanceUtil.MAIN_THREAD;
            DistanceUtil.LOGGER
                .info("Chunk ({},{}) executing on main thread", thisThing.xPosition, thisThing.zPosition);
            return;
        }

        DistanceUtil.setExecutor(this, entry.get().player);
    }

    private final class Entry {

        private final EntityPlayer player;
        private final long distance;

        private Entry(EntityPlayer player) {
            this.player = player;
            long dx = thisThing.xPosition - player.chunkCoordX;
            long dz = thisThing.zPosition - player.chunkCoordZ;
            this.distance = (dx * dx) + (dz * dz);
        }
    }
}
