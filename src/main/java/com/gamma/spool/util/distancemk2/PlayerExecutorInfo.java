package com.gamma.spool.util.distancemk2;

import java.util.List;
import java.util.concurrent.Executor;

import net.minecraft.entity.player.EntityPlayer;

import com.gamma.spool.config.DistanceThreadingConfig;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class PlayerExecutorInfo extends AbstractExecutorInfo<EntityPlayer> {

    public PlayerExecutorInfo(EntityPlayer thisPlayer) {
        super(thisPlayer);
    }

    @Override
    public void notifyOfChange(EntityPlayer player) {
        if (player == thisThing) recalculateExecutor();
    }

    @Override
    protected synchronized void recalculateExecutor() {
        // Rebuild based on all players in the server (optionally parallel)
        List<EntityPlayer> playerList = thisThing.worldObj.playerEntities;
        List<EntityPlayer> nearbyPlayers;
        if (DistanceThreadingConfig.streamParallelizationLevel > 0 && playerList.size() > 1) {
            // find nearest player
            nearbyPlayers = playerList.parallelStream()
                .filter(player -> player != thisThing)
                .filter(player -> DistanceUtil.isPlayerInsidePlayerRange(thisThing, player))
                .collect(ObjectArrayList::new, ObjectArrayList::add, ObjectArrayList::addAll);
        } else {
            nearbyPlayers = new ObjectArrayList<>();
            for (EntityPlayer player : playerList) {
                if (thisThing == player) continue;
                if (DistanceUtil.isPlayerInsidePlayerRange(thisThing, player)) {
                    nearbyPlayers.add(player);
                }
            }
        }

        if (nearbyPlayers.isEmpty()) {
            // If there are no nearby players, we should have our own executor.
            if (executor != null) return; // Reuse the current executor if we already have one.
            // Request a new executor.
            executor = DistanceUtil.requestNewExecutor(thisThing);
            DistanceUtil.notifyExecutorReferenceAdded(executor);
            return;
        }
        // There are players nearby, so now we need to synchronize them all together.
        executor = synchronizeAllExecutors(nearbyPlayers);
    }

    private Executor synchronizeAllExecutors(List<EntityPlayer> nearbyPlayers) {
        if (DistanceThreadingConfig.streamParallelizationLevel > 1 && nearbyPlayers.size() > 1) {
            nearbyPlayers.parallelStream()
                .forEach(player -> DistanceUtil.setExecutor(player, this.executor));
        } else {
            for (EntityPlayer player : nearbyPlayers) {
                DistanceUtil.setExecutor(player, this.executor);
            }
        }
        DistanceUtil.LOGGER
            .info("Synchronized {} players on {}'s executor", nearbyPlayers.size(), thisThing.getCommandSenderName());
        return executor;
    }
}
