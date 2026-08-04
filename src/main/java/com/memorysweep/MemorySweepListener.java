package com.memorysweep;

import com.memorysweep.config.MemorySweepConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dimdev.rift.listener.CommandAdder;
import org.dimdev.rift.listener.MinecraftStartListener;
import org.dimdev.rift.listener.ServerTickable;

/**
 * MemorySweep 的主监听类,同时实现三个 Rift 监听接口:
 * <ul>
 *   <li>{@link MinecraftStartListener} —— 游戏启动完成时打印一次启动信息</li>
 *   <li>{@link ServerTickable} —— 每个服务器 tick 驱动定时清理 / 使用率触发清理</li>
 *   <li>{@link CommandAdder} —— 注册 {@code /memorysweep} 指令(1.13.2 原生 Brigadier 系统)</li>
 * </ul>
 * 在 riftmod.json 的 "listeners" 里只需要注册这一个类,Rift 会自动按接口类型分别调用。
 */
public class MemorySweepListener implements MinecraftStartListener, ServerTickable, CommandAdder {

    public static final Logger LOGGER = LogManager.getLogger("MemorySweep");

    private static MemorySweepConfig config;
    private static MemoryMonitor memoryMonitor;

    @Override
    public void onMinecraftStart() {
        config = MemorySweepConfig.load(LOGGER);
        memoryMonitor = new MemoryMonitor(LOGGER, config.logToConsole);

        LOGGER.info("[MemorySweep] 模组已加载 | 定时清理: "
                + (config.autoCleanupEnabled ? ("每 " + config.intervalMinutes + " 分钟一次") : "已禁用")
                + " | 使用率触发清理: "
                + (config.usageBasedCleanupEnabled
                        ? ("已启用(阈值 " + config.memoryUsageThresholdPercent + "%,冷却 " + config.usageCheckCooldownSeconds + " 秒)")
                        : "已禁用"));
    }

    @Override
    public void serverTick(MinecraftServer server) {
        if (config == null || memoryMonitor == null) {
            return; // 理论上不会发生(onMinecraftStart 总是先于 tick 执行),保险起见判空
        }

        memoryMonitor.onServerTick(config.autoCleanupEnabled, config.intervalMinutes,
                config.usageBasedCleanupEnabled, config.memoryUsageThresholdPercent,
                config.usageCheckCooldownSeconds, config.usageCheckIntervalSeconds);
    }

    @Override
    public void registerCommands(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("memorysweep")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(this::executeSweep)
                .then(Commands.literal("status").executes(this::executeStatus)));
    }

    private int executeSweep(CommandContext<CommandSource> context) {
        if (memoryMonitor == null) {
            context.getSource().sendErrorMessage(new TextComponentString("MemorySweep 尚未初始化完成,请稍后再试。"));
            return 0;
        }

        MemoryMonitor.CleanupResult result = memoryMonitor.performCleanup(MemoryMonitor.CleanupReason.MANUAL);
        context.getSource().sendFeedback(new TextComponentString(result.toChatText()), true);
        return 1;
    }

    private int executeStatus(CommandContext<CommandSource> context) {
        if (memoryMonitor == null || config == null) {
            context.getSource().sendErrorMessage(new TextComponentString("MemorySweep 尚未初始化完成,请稍后再试。"));
            return 0;
        }

        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long max = runtime.maxMemory() / (1024 * 1024);
        double percent = memoryMonitor.currentUsagePercent();

        String status = String.format(java.util.Locale.ROOT,
                "[内存清理状态] 当前使用 %d/%d MB (%.1f%%) | 定时清理: %s | 使用率触发: %s",
                used, max, percent,
                config.autoCleanupEnabled ? ("每 " + config.intervalMinutes + " 分钟一次") : "已禁用",
                config.usageBasedCleanupEnabled
                        ? ("已启用(阈值 " + config.memoryUsageThresholdPercent + "%,冷却 " + config.usageCheckCooldownSeconds + " 秒)")
                        : "已禁用");

        context.getSource().sendFeedback(new TextComponentString(status), false);
        return 1;
    }
}
