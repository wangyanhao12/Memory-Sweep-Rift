package com.memorysweep;

import org.apache.logging.log4j.Logger;

import java.util.Locale;

/**
 * 内存监控与清理的核心逻辑,与具体触发方式(定时 tick、指令)解耦。
 * Java 8 兼容写法(Rift/1.13.2 时代要求 sourceCompatibility 1.8),不使用 record/var。
 *
 * <p>支持的触发方式:</p>
 * <ul>
 *   <li>定时:每隔 {@code intervalMinutes} 分钟(默认 15 分钟)清理一次</li>
 *   <li>使用率:堆内存占用达到 {@code memoryUsageThresholdPercent}(默认 80%)时清理,
 *       但距离上一次清理(无论是何种触发方式)不足 {@code usageCheckCooldownSeconds}
 *       (默认 120 秒,即 2 分钟)时不会重复触发。</li>
 *   <li>手动:玩家/控制台执行 {@code /memorysweep}(1.13.2 原生 Brigadier 指令系统)。</li>
 * </ul>
 */
public final class MemoryMonitor {

    public enum CleanupReason {
        MANUAL("手动清理"),
        SCHEDULED("定时自动清理"),
        USAGE_TRIGGERED("内存使用率触发清理");

        private final String label;

        CleanupReason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final class CleanupResult {
        private final long beforeUsedBytes;
        private final long afterUsedBytes;
        private final long maxBytes;
        private final long durationMillis;
        private final CleanupReason reason;

        CleanupResult(long beforeUsedBytes, long afterUsedBytes, long maxBytes, long durationMillis, CleanupReason reason) {
            this.beforeUsedBytes = beforeUsedBytes;
            this.afterUsedBytes = afterUsedBytes;
            this.maxBytes = maxBytes;
            this.durationMillis = durationMillis;
            this.reason = reason;
        }

        public long freedBytes() {
            return Math.max(0L, beforeUsedBytes - afterUsedBytes);
        }

        public double beforePercent() {
            return maxBytes <= 0 ? 0.0 : (beforeUsedBytes * 100.0) / maxBytes;
        }

        public double afterPercent() {
            return maxBytes <= 0 ? 0.0 : (afterUsedBytes * 100.0) / maxBytes;
        }

        public String toLogText() {
            return String.format(Locale.ROOT,
                    "%s完成 | 清理前 %d MB (%.1f%%) -> 清理后 %d MB (%.1f%%) | 释放约 %d MB | 耗时 %d ms",
                    reason.label(), toMb(beforeUsedBytes), beforePercent(), toMb(afterUsedBytes), afterPercent(),
                    toMb(freedBytes()), durationMillis);
        }

        public String toChatText() {
            return String.format(Locale.ROOT, "[内存清理] %s | %d MB -> %d MB | 释放约 %d MB | 耗时 %d ms",
                    reason.label(), toMb(beforeUsedBytes), toMb(afterUsedBytes), toMb(freedBytes()), durationMillis);
        }

        private static long toMb(long bytes) {
            return bytes / (1024L * 1024L);
        }
    }

    private final Logger logger;
    private final boolean logToConsole;

    private long lastCleanupTimeMillis = 0L;
    private long lastScheduledCleanupTimeMillis = 0L;
    private int tickCounter = 0;

    public MemoryMonitor(Logger logger, boolean logToConsole) {
        this.logger = logger;
        this.logToConsole = logToConsole;
    }

    /** 每个服务器 tick 调用一次。内部按秒节流,避免每 tick 都做时间/内存运算。 */
    public void onServerTick(boolean autoCleanupEnabled, int intervalMinutes, boolean usageBasedCleanupEnabled,
            int memoryUsageThresholdPercent, int usageCheckCooldownSeconds, int usageCheckIntervalSeconds) {
        tickCounter++;
        if (tickCounter < 20) { // 20 tick ≈ 1 秒(服务器满速运行时)
            return;
        }
        tickCounter = 0;

        long now = System.currentTimeMillis();

        if (lastScheduledCleanupTimeMillis == 0L) {
            lastScheduledCleanupTimeMillis = now; // 第一次 tick,从此刻开始计时定时清理
        }

        if (autoCleanupEnabled) {
            long intervalMillis = intervalMinutes * 60_000L;
            if (now - lastScheduledCleanupTimeMillis >= intervalMillis) {
                lastScheduledCleanupTimeMillis = now;
                performCleanup(CleanupReason.SCHEDULED);
            }
        }

        if (usageBasedCleanupEnabled) {
            if (currentUsagePercent() >= memoryUsageThresholdPercent) {
                long cooldownMillis = usageCheckCooldownSeconds * 1000L;
                if (now - lastCleanupTimeMillis >= cooldownMillis) {
                    performCleanup(CleanupReason.USAGE_TRIGGERED);
                }
            }
        }
    }

    /** 当前堆内存使用率(0-100)。 */
    public double currentUsagePercent() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        if (max <= 0) {
            return 0.0;
        }
        long used = runtime.totalMemory() - runtime.freeMemory();
        return (used * 100.0) / max;
    }

    /**
     * 立即执行一次内存清理(调用 {@link System#gc()}),并根据配置输出日志。
     * 该方法本身不做冷却判断 —— 冷却只用于限制"使用率自动触发"。
     */
    public CleanupResult performCleanup(CleanupReason reason) {
        Runtime runtime = Runtime.getRuntime();
        long beforeUsed = runtime.totalMemory() - runtime.freeMemory();

        long startNanos = System.nanoTime();
        System.gc();
        long durationMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        long afterUsed = runtime.totalMemory() - runtime.freeMemory();

        lastCleanupTimeMillis = System.currentTimeMillis();

        CleanupResult result = new CleanupResult(beforeUsed, afterUsed, runtime.maxMemory(), durationMillis, reason);

        if (logToConsole) {
            logger.info("[MemorySweep] " + result.toLogText());
        }

        return result;
    }
}
