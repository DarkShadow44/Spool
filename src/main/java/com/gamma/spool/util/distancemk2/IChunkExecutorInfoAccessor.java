package com.gamma.spool.util.distancemk2;

import java.util.concurrent.Executor;

public interface IChunkExecutorInfoAccessor extends IExecutorAccessor {

    ChunkExecutorInfo spool$getChunkExecutorInfo();

    @Override
    default Executor getExecutor() {
        return spool$getChunkExecutorInfo().getExecutor();
    }
}
