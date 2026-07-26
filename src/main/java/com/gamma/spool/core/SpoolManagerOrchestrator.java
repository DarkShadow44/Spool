package com.gamma.spool.core;

import java.util.EnumMap;
import java.util.Map;

import com.gamma.spool.api.annotations.SkipSpoolASMChecks;
import com.gamma.spool.config.ThreadManagerConfig;
import com.gamma.spool.config.ThreadsConfig;
import com.gamma.spool.thread.ForkThreadManager;
import com.gamma.spool.thread.IThreadManager;
import com.gamma.spool.thread.KeyedPoolThreadManager;
import com.gamma.spool.thread.LBKeyedPoolThreadManager;
import com.gamma.spool.thread.ManagerNames;
import com.gamma.spool.thread.TimedOperationThreadManager;
import com.gamma.spool.util.caching.RegisteredCache;

@SkipSpoolASMChecks(SkipSpoolASMChecks.SpoolASMCheck.ALL)
public class SpoolManagerOrchestrator {

    public static final Map<ManagerNames, IThreadManager> REGISTERED_THREAD_MANAGERS = new EnumMap<>(
        ManagerNames.class);

    public static final Map<ManagerNames, RegisteredCache> REGISTERED_CACHES = new EnumMap<>(ManagerNames.class);

    static void startPools() {

        REGISTERED_THREAD_MANAGERS.put(
            ManagerNames.THREAD_MANAGER_TIMER,
            new TimedOperationThreadManager(ManagerNames.THREAD_MANAGER_TIMER.getName(), 1));
        SpoolLogger.info("Thread manager timer initialized.");

        if (ThreadsConfig.isDimensionThreadingEnabled()) {

            KeyedPoolThreadManager dimensionManager;
            if (ThreadManagerConfig.useLoadBalancingDimensionThreadManager)
                dimensionManager = new LBKeyedPoolThreadManager(
                    ManagerNames.DIMENSION.getName(),
                    ThreadsConfig.dimensionMaxThreads);
            else dimensionManager = new KeyedPoolThreadManager(
                ManagerNames.DIMENSION.getName(),
                ThreadsConfig.dimensionMaxThreads);

            REGISTERED_THREAD_MANAGERS.put(ManagerNames.DIMENSION, dimensionManager);
            SpoolLogger.info("Dimension manager initialized.");
        }

        if (ThreadsConfig.isEntityAIThreadingEnabled()) {
            REGISTERED_THREAD_MANAGERS.put(
                ManagerNames.ENTITY_AI,
                new ForkThreadManager(ManagerNames.ENTITY_AI.getName(), ThreadsConfig.entityAIMaxThreads));
            SpoolLogger.info("Entity AI manager initialized.");
        }
    }
}
