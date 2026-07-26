package com.gamma.spool.util.distancemk2;

import java.util.concurrent.Executor;

public abstract class AbstractExecutorInfo<T> extends AbstractNotifiable<T> {

    protected final T thisThing;
    protected Executor executor;

    public AbstractExecutorInfo(T thisThing) {
        this.thisThing = thisThing;
    }

    public synchronized Executor getExecutor() {
        if (executor == null) recalculateExecutor();
        if (executor == null) throw new IllegalStateException("Executor is null after recalculating!");
        return executor;
    }

    protected abstract void recalculateExecutor();
}
