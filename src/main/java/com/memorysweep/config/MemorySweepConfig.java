package com.memorysweep.config;

import org.apache.logging.log4j.Logger;
import org.dimdev.riftloader.RiftLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * MemorySweep 的配置文件,对应磁盘上的 config/memorysweep.properties。
 * <p>
 * 使用 JDK 自带的 {@link Properties} 读写简单的 key=value 配置文件,不额外引入其他依赖
 * (Java 8 兼容写法,不使用 var/record 等较新语法)。
 */
public final class MemorySweepConfig {

    private static final String FILE_NAME = "memorysweep.properties";

    /** 是否启用"定时自动清理"。 */
    public boolean autoCleanupEnabled = true;

    /** 定时自动清理的间隔时间,单位:分钟。默认 15 分钟。 */
    public int intervalMinutes = 15;

    /** 是否启用"根据内存使用率自动清理"。 */
    public boolean usageBasedCleanupEnabled = true;

    /** 触发使用率清理的堆内存占用阈值,单位:百分比(1-99)。默认 80。 */
    public int memoryUsageThresholdPercent = 80;

    /** 使用率触发的清理,两次执行之间的最短间隔,单位:秒。默认 120 秒(2 分钟)。 */
    public int usageCheckCooldownSeconds = 120;

    /** 后台检查内存使用率的频率,单位:秒。默认 5 秒检查一次。 */
    public int usageCheckIntervalSeconds = 5;

    /** 每次清理后,是否在控制台/日志中输出清理结果。 */
    public boolean logToConsole = true;

    public static MemorySweepConfig load(Logger logger) {
        File file = configFile();
        MemorySweepConfig config = new MemorySweepConfig();

        if (file.exists()) {
            Properties props = new Properties();
            InputStream in = null;
            try {
                in = new FileInputStream(file);
                props.load(in);
                config.autoCleanupEnabled = parseBoolean(props, "autoCleanupEnabled", config.autoCleanupEnabled);
                config.intervalMinutes = parseInt(props, "intervalMinutes", config.intervalMinutes);
                config.usageBasedCleanupEnabled = parseBoolean(props, "usageBasedCleanupEnabled", config.usageBasedCleanupEnabled);
                config.memoryUsageThresholdPercent = parseInt(props, "memoryUsageThresholdPercent", config.memoryUsageThresholdPercent);
                config.usageCheckCooldownSeconds = parseInt(props, "usageCheckCooldownSeconds", config.usageCheckCooldownSeconds);
                config.usageCheckIntervalSeconds = parseInt(props, "usageCheckIntervalSeconds", config.usageCheckIntervalSeconds);
                config.logToConsole = parseBoolean(props, "logToConsole", config.logToConsole);
            } catch (IOException e) {
                logger.warn("[MemorySweep] 配置文件读取失败,将使用默认配置覆盖: " + e.getMessage());
            } finally {
                closeQuietly(in);
            }
        }

        config.sanitize();
        config.save(logger);
        return config;
    }

    public void save(Logger logger) {
        File file = configFile();
        Properties props = new Properties();
        props.setProperty("autoCleanupEnabled", String.valueOf(autoCleanupEnabled));
        props.setProperty("intervalMinutes", String.valueOf(intervalMinutes));
        props.setProperty("usageBasedCleanupEnabled", String.valueOf(usageBasedCleanupEnabled));
        props.setProperty("memoryUsageThresholdPercent", String.valueOf(memoryUsageThresholdPercent));
        props.setProperty("usageCheckCooldownSeconds", String.valueOf(usageCheckCooldownSeconds));
        props.setProperty("usageCheckIntervalSeconds", String.valueOf(usageCheckIntervalSeconds));
        props.setProperty("logToConsole", String.valueOf(logToConsole));

        OutputStream out = null;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            out = new FileOutputStream(file);
            props.store(out, "MemorySweep config - restart the game/server after editing for changes to take effect");
        } catch (IOException e) {
            logger.warn("[MemorySweep] 配置文件保存失败: " + e.getMessage());
        } finally {
            closeQuietly(out);
        }
    }

    private void sanitize() {
        if (intervalMinutes < 1) {
            intervalMinutes = 1;
        }
        if (memoryUsageThresholdPercent < 1) {
            memoryUsageThresholdPercent = 1;
        } else if (memoryUsageThresholdPercent > 99) {
            memoryUsageThresholdPercent = 99;
        }
        if (usageCheckCooldownSeconds < 1) {
            usageCheckCooldownSeconds = 1;
        }
        if (usageCheckIntervalSeconds < 1) {
            usageCheckIntervalSeconds = 1;
        }
    }

    private static boolean parseBoolean(Properties props, String key, boolean fallback) {
        String value = props.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static int parseInt(Properties props, String key, int fallback) {
        String value = props.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // 忽略关闭时的异常
            }
        }
    }

    private static File configFile() {
        return new File(RiftLoader.instance.configDir, FILE_NAME);
    }
}
