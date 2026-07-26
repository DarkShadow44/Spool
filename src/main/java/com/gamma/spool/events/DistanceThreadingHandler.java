package com.gamma.spool.events;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.event.entity.EntityEvent;

import com.gamma.spool.util.distancemk2.Notifier;
import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

@EventBusSubscriber
public class DistanceThreadingHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Notifier.playerLeftWorld(event.player.worldObj);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Notifier.playerEnteredWorld(event.player.worldObj);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Notifier.playerLeftWorld(DimensionManager.getWorld(event.fromDim));
        Notifier.playerLeftWorld(DimensionManager.getWorld(event.toDim));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onChunkForced(ForgeChunkManager.ForceChunkEvent event) {
        Notifier.chunkForceLoading(event.ticket.world, event.location);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onChunkUnforced(ForgeChunkManager.UnforceChunkEvent event) {
        Notifier.chunkStopForceLoading(event.ticket.world, event.location);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerEnterChunk(EntityEvent.EnteringChunk event) {
        if (event.entity instanceof EntityPlayer) Notifier.playerChangedChunks(event.entity.worldObj);
    }
}
