package com.xiaoce.agent.caffine;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * CaffineProperties
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/26 13:08
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "caffine")
public class CaffineProperties {
        private cache cache = new cache();
        private hotkey hotkey = new hotkey();

        @Data
        public static class  cache{
                /**
                 * 本地缓存最大容量
                 */
                private long maxSize = 500000;

                /**
                 * 缓存过期时间（分钟）
                 */
                private int expireMinutes = 5;

                /**
                 * 热点统计窗口大小（分钟）
                 */
                private int hotWindowMinutes = 5;

                /**
                 * 热点阈值（窗口内查询次数）
                 */
                private int hotThreshold = 1000;

                /**
                 * 预热数量
                 */
                private int preheatCount = 1000;

                private int segmentSeconds = 10;
        }
        @Data
        public static class  hotkey{
                // 热点统计窗口长度（秒）。
                private int windowSeconds = 60;

                // 统计窗口切片大小（秒），用于按段累计访问次数。
                private int segmentSeconds = 10;

                // 低热度阈值：窗口内访问次数达到该值视为低热。
                private int levelLow = 50;

                // 中热度阈值：窗口内访问次数达到该值视为中热。
                private int levelMedium = 200;

                // 高热度阈值：窗口内访问次数达到该值视为高热。
                private int levelHigh = 500;

                // 低热度额外延长 TTL（秒）。
                private int extendLowSeconds = 20;

                //中热度额外延长 TTL（秒）。
                private int extendMediumSeconds = 60;

                // 高热度额外延长 TTL（秒）。
                private int extendHighSeconds = 120;
        }


}
