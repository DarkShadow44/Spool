package com.gamma.spool.mixin.minecraft;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.gamma.spool.util.distancemk2.IPlayerExecutorInfoAccessor;
import com.gamma.spool.util.distancemk2.PlayerExecutorInfo;

@Mixin(EntityPlayer.class)
public abstract class EntityPlayerMixin implements IPlayerExecutorInfoAccessor {

    @Unique
    private final PlayerExecutorInfo spool$playerExecutorInfo = new PlayerExecutorInfo((EntityPlayer) (Object) this);

    @Override
    public PlayerExecutorInfo spool$getPlayerExecutorInfo() {
        return spool$playerExecutorInfo;
    }
}
