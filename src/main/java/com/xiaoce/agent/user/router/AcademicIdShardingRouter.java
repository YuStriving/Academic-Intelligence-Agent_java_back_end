package com.xiaoce.agent.user.router;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AcademicIdShardingRouter
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/26 12:33
 */
@Component
@RequiredArgsConstructor
public class AcademicIdShardingRouter {
    private final StringRedisTemplate redisTemplate;
    /**
     * 分片数量（建议 16 或 32）
     * 1 亿数据 / 16 分片 = 每片约 625 万
     */
    private static final int SHARD_COUNT = 16;
    // 分片到哪一块的set集合的前缀，example user:academic_id:shard:1 ,分片到第一块set上
    private static final String SHARD_KEY_PREFIX = "user:academic_id:shard:";
    private static final String BLOOM_KEY = "academic_id:bloom";


    /**
     * 64位FNV-1a 初始偏移量（官方标准）
     */
    private static final long FNV1A_64_OFFSET_BASIS = 0xcbf29ce484222325L;
    /**
     * 64位FNV-1a 质数（官方标准）
     */
    private static final long FNV1A_64_PRIME = 0x100000001b3L;


    /**
     * 根据学术ID计算分片索引
     * 使用 FNV-1a 哈希算法，分布更均匀
     *
     * @param academicId 学术ID
     * @return 分片索引 (0 到 SHARD_COUNT-1)
     */
    public int getShardIndex(String academicId) {
        long hash = fnv1a64Hash(academicId);
        return (int) (hash % SHARD_COUNT);
    }
    /**
     * FNV-1a 哈希算法
     * 比 String.hashCode() 分布更均匀，减少哈希冲突
     *
     * @param str 输入字符串
     * @return 哈希值
     */
    private long fnv1a64Hash(String str) {
        if (str == null || str.isEmpty()) {
            return 0;
        }

        long hash = FNV1A_64_OFFSET_BASIS;
        for (char c : str.toCharArray()) {
            hash ^= c;
            hash *= FNV1A_64_PRIME;
        }

        //直接在这里返回绝对值，永远非负！
        return Math.abs(hash);
    }

    public String buildAcademicShardKey(int shardIndex){
        return SHARD_KEY_PREFIX + shardIndex;
    }

    /**
     * 添加学术ID到对应分片Set集合当中
     *
     * @param academicId 学术ID
     */
    public void addAcademicId(String academicId) {
        int shardIndex = getShardIndex(academicId);
        String shardKey = buildAcademicShardKey(shardIndex);
        redisTemplate.opsForSet().add(shardKey, academicId);
    }

    /**
     * 批量添加学术ID
     *
     * @param academicIds 学术ID列表
     */
    public void addAcademicIdBatch(List<String> academicIds) {
        // 按分片分组
        Map<Integer, List<String>> shardMap = new HashMap<>();
        for (String id : academicIds) {
            int shardIndex = getShardIndex(id);
            shardMap.computeIfAbsent(shardIndex, k -> new ArrayList<>()).add(id);
        }

        // 批量写入各分片
        for (Map.Entry<Integer, List<String>> entry : shardMap.entrySet()) {
            int shardIndex = entry.getKey();
            String shardKey = buildAcademicShardKey(shardIndex);

            // 使用 Pipeline 批量操作，提高性能
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                //拿到 List<String>存储的academicId
                for (String id : entry.getValue()) {
                    connection.setCommands().sAdd(
                            shardKey.getBytes(StandardCharsets.UTF_8),
                            id.getBytes(StandardCharsets.UTF_8));
                }
                return null;
            });
        }
    }

    /**
     * 删除学术ID（仅在数据修正时使用）
     *
     * @param academicId 学术ID
     */
    public void removeAcademicId(String academicId) {
        int shardIndex = getShardIndex(academicId);
        String shardKey = buildAcademicShardKey(shardIndex);

        redisTemplate.opsForSet().remove(shardKey, academicId);
    }

    /**
     * 获取指定分片的数据量
     *
     * @param shardIndex 分片索引
     * @return 该分片中的学术ID数量
     */
    public Long getShardSize(int shardIndex) {
        String shardKey = buildAcademicShardKey(shardIndex);
        return redisTemplate.opsForSet().size(shardKey);
    }

    /**
     * 获取所有分片的总数据量
     *
     * @return 所有分片中的学术ID总数
     */
    public long getTotalSize() {
        long total = 0;
        for (int i = 0; i < SHARD_COUNT; i++) {
            Long size = getShardSize(i);
            if (size != null) {
                total += size;
            }
        }
        return total;
    }



}
