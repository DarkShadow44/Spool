package com.gamma.spool.util.distancemk2;

import java.util.concurrent.Executor;

public interface IPlayerExecutorInfoAccessor extends IExecutorAccessor {

    PlayerExecutorInfo spool$getPlayerExecutorInfo();

    @Override
    default Executor getExecutor() {
        return spool$getPlayerExecutorInfo().getExecutor();
    }
}
