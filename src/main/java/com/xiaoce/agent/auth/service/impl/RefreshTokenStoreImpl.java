package com.xiaoce.agent.auth.service.impl;

import com.xiaoce.agent.auth.enums.ClientType;
import com.xiaoce.agent.auth.service.IRefreshTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.xiaoce.agent.auth.common.constant.AuthRedisConstants.GLOBAL_EXPIRY_ZSET_KEY;
import static com.xiaoce.agent.auth.common.constant.AuthRedisConstants.TOKEN_KEY_PREFIX;
import static com.xiaoce.agent.auth.common.constant.AuthRedisConstants.USER_SET_KEY_PREFIX;

/**
 * 刷新令牌Redis存储服务（Redis存储管理器）
 * 
 * <p>这个类负责将JWT刷新令牌（refreshToken）的状态存储到Redis中。
 * 它是JWT + Redis混合方案的核心组件，解决了JWT本身无法主动撤销的问题。
 * 
 * <p>为什么需要存储refreshToken到Redis？
 * <pre>
 * JWT的缺陷：
 * 1. JWT一旦签发就无法作废，直到过期为止
 * 2. 用户登出时，无法让已签发的JWT失效
 * 3. 修改密码后，旧JWT仍然可以使用
 * 
 * 解决方案：
 * 1. 将refreshToken的标识（jti）存储到Redis
 * 2. 用户登出时，从Redis删除对应的refreshToken
 * 3. 验证refreshToken时，先检查Redis中是否存在
 * 4. 如果Redis中不存在，说明已被登出，令牌失效
 * </pre>
 * 
 * <p>Redis数据结构设计（三种数据结构配合使用）：
 * <pre>
 * 1. String类型：存储单个令牌的归属关系
 *    Key: token:{jti}
 *    Value: {userId}
 *    TTL: 令牌的剩余有效期
 *    用途：验证令牌是否属于某个用户，防止越权操作
 *    示例：token:abc123-uuid -> "1001"（表示这个令牌属于用户1001）
 * 
 * 2. ZSet类型（用户维度）：存储某个用户的所有有效令牌
 *    Key: user:tokens:{userId}
 *    Member: {jti}
 *    Score: 过期时间戳（用于按时间排序和查询）
 *    用途：快速查询用户有多少有效令牌，支持单点登录、全设备登出
 *    示例：user:tokens:1001 -> {abc123: 1700000000, def456: 1700003600}
 * 
 * 3. ZSet类型（全局维度）：存储所有用户的所有令牌（用于过期清理）
 *    Key: auth:tokens:global:expiry
 *    Member: {userId}|{jti}
 *    Score: 过期时间戳
 *    用途：定时任务批量清理过期令牌，避免数据堆积
 *    示例：auth:tokens:global:expiry -> {1001|abc123: 1700000000}
 * </pre>
 * 
 * <p>Lua脚本保证原子性：
 * <pre>
 * 为什么要用Lua脚本？
 * 1. Redis的单命令是原子的，但多命令组合不是
 * 2. 如果不用Lua，可能出现并发问题（如验证和删除之间被插入其他操作）
 * 3. Lua脚本在Redis中是原子执行的，不会被其他命令打断
 * 
 * 本类使用了4个Lua脚本：
 * - SAVE_SCRIPT：保存令牌（同时写入3种数据结构）
 * - VALIDATE_SCRIPT：验证令牌归属（防止越权）
 * - REMOVE_SCRIPT：删除令牌（安全删除，检查归属）
 * - CLEANUP_BATCH_SCRIPT：批量清理过期令牌
 * </pre>
 * 
 * <p>与其他组件的配合：
 * <ul>
 *   <li>AuthServiceImpl：调用本服务保存、验证、删除refreshToken</li>
 *   <li>JwtTokenService：生成refreshToken的jti，传递给本服务</li>
 *   <li>RefreshTokenCleanupTask：定时调用cleanupExpiredTokens清理过期数据</li>
 *   <li>AuthRedisConstants：提供Redis key的前缀常量</li>
 * </ul>
 * 
 * <p>使用场景：
 * <ul>
 *   <li>用户登录成功后，保存refreshToken到Redis</li>
 *   <li>用户刷新令牌时，验证refreshToken是否有效</li>
 *   <li>用户登出时，从Redis删除refreshToken</li>
 *   <li>用户全设备登出时，删除该用户所有refreshToken</li>
 *   <li>定时任务清理所有过期令牌，释放Redis内存</li>
 * </ul>
 * 
 * @author xiaoce
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenStoreImpl implements IRefreshTokenStore {

    // ==================== 常量定义 ====================
    
    /**
     * 用户令牌ZSet的key后缀
     * 完整格式：user:tokens:{userId}
     */
    private static final String USER_TOKEN_KEY_SUFFIX = ":tokens";
    
    /**
     * 全局ZSet中member的分隔符
     * 格式：{userId}|{jti}，使用|分隔用户ID和令牌ID
     */
    private static final String GLOBAL_MEMBER_SEPARATOR = "|";
    
    /**
     * 批量清理过期令牌的默认批次大小
     * 每次清理200个，避免单次操作阻塞Redis过久
     */
    private static final int DEFAULT_CLEANUP_BATCH_SIZE = 200;

    // ==================== Lua脚本定义 ====================
    /**
     * 创建Lua脚本对象（工具方法）
     *
     * <p>简化Lua脚本的创建过程，设置脚本内容和返回值类型。
     *
     * @param scriptText Lua脚本的文本内容
     * @return 配置好的DefaultRedisScript对象
     */
    private static DefaultRedisScript<Long> longScript(String scriptText) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(Long.class);
        return script;
    }
    /**
     * 保存刷新令牌的Lua脚本
     * 
     * <p>这个脚本原子性地将令牌信息写入Redis的3个数据结构中。
     * 
     * <p>脚本逻辑：
     * <pre>
     * 输入参数：
     * KEYS[1] = token:{jti}             （String key，存储令牌归属）
     * KEYS[2] = user:tokens:{userId}    （ZSet key，存储用户令牌列表）
     * KEYS[3] = auth:tokens:global:expiry （ZSet key，存储全局令牌列表）
     * 
     * ARGV[1] = userId                  （用户ID，作为String的value）
     * ARGV[2] = expireAtEpochSeconds    （过期时间戳，作为ZSet的score）
     * ARGV[3] = ttlSeconds              （TTL秒数，用于设置过期时间）
     * ARGV[4] = jti                     （令牌ID，作为用户ZSet的member）
     * ARGV[5] = {userId}|{jti}          （组合格式，作为全局ZSet的member）
     * 
     * 执行步骤：
     * 1. SET token:{jti} {userId} EX {ttlSeconds}
     *    - 存储令牌归属关系，设置TTL自动过期
     * 
     * 2. ZADD user:tokens:{userId} {expireAt} {jti}
     *    - 将令牌加入用户的令牌集合，用过期时间作为score
     * 
     * 3. ZADD auth:tokens:global:expiry {expireAt} {userId}|{jti}
     *    - 将令牌加入全局集合，用于定时清理
     * 
     * 4. 检查并更新用户ZSet的TTL
     *    - 如果用户ZSet的TTL小于期望值，则延长TTL
     *    - 这样可以防止用户频繁登录导致ZSet过早过期
     * </pre>
     * 
     * <p>为什么用Lua脚本？
     * 如果用普通Redis命令，需要执行4个命令，不是原子操作。
     * 并发场景下可能出现数据不一致（如只写入了String，ZSet还没写入）。
     * Lua脚本保证4个操作要么全成功，要么全失败。
     */

    private static final DefaultRedisScript<Long> SAVE_SCRIPT = longScript(
            "-- 步骤1：保存新的刷新令牌到String（用于验证归属）\n" +
            "redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])\n" +
            "-- 步骤2：添加到用户维度的ZSet（按过期时间排序）\n" +
            "redis.call('ZADD', KEYS[2], ARGV[2], ARGV[4])\n" +
            "-- 步骤3：添加到全局维度的ZSet（用于批量清理过期令牌）\n" +
            "redis.call('ZADD', KEYS[3], ARGV[2], ARGV[5])\n" +
            "-- 步骤4：TTL管理（确保用户维度的ZSet不会过早过期）\n" +
            "local currentTtl = redis.call('TTL', KEYS[2])\n" +
            "local desiredTtl = tonumber(ARGV[3])\n" +
            "if currentTtl < desiredTtl then \n" +
            "    redis.call('EXPIRE', KEYS[2], desiredTtl)\n" +
            "end\n" +
            "-- 步骤5：按客户端类型分组统计并清理旧令牌（核心逻辑）\n" +
            "local maxPerClient = tonumber(ARGV[7])\n" +  // 每个客户端类型最大允许数（通常为1）
            "if maxPerClient == nil then maxPerClient = 1 end\n" + // 安全性校验
            "\n" +
            "-- 获取用户维度ZSet所有成员（已按score升序排列）\n" +
            "local allMembers = redis.call('ZRANGE', KEYS[2], 0, -1)\n" +
            "local clientTypeGroups = {}\n" +
            "\n" +
            "-- 按客户端类型前缀分组（格式：{clientType}|{jti}）\n" +
            "for i, member in ipairs(allMembers) do\n" +
            "    local prefix = string.match(member, '^(.-)|')\n" +
            "    if prefix then\n" +
            "        if not clientTypeGroups[prefix] then\n" +
            "            clientTypeGroups[prefix] = {}\n" +
            "        end\n" +
            "        table.insert(clientTypeGroups[prefix], member)\n" +
            "    end\n" +
            "end\n" +
            "\n" +
            "-- 对每种客户端类型，如果数量超过maxPerClient，删除最旧的\n" + // maxPerClient也就是1
            "for clientType, members in pairs(clientTypeGroups) do\n" +
            "    local count = #members\n" +
            "    if count > maxPerClient then\n" +
            "        local toRemoveCount = count - maxPerClient\n" +
            "        for i = 1, toRemoveCount do\n" +
            "            local oldMember = members[i]\n" +
            "            \n" +
            "            -- 从User ZSet删除\n" +
            "            redis.call('ZREM', KEYS[2], oldMember)\n" +
            "            \n" +
            "            -- 解析jti并删除对应的String key\n" +
            "            local oldJti = string.match(oldMember, '|(.+)$')\n" +
            "            if oldJti then\n" +
            "                local oldTokenKey = ARGV[6] .. clientType .. ':' .. oldJti\n" +
            "                redis.call('DEL', oldTokenKey)\n" +
            "                \n" +
            "                -- 从Global ZSet删除\n" +
            "                local oldGlobalMember = ARGV[1] .. '|' .. oldJti\n" +
            "                redis.call('ZREM', KEYS[3], oldGlobalMember)\n" +
            "            end\n" +
            "        end\n" +
            "    end\n" +
            "end\n" +
            "\n" +
            "return 1"
    );

    /**
     * 验证刷新令牌的Lua脚本
     * 
     * <p>检查令牌是否存在且属于指定用户，防止越权操作。
     * 
     * <p>脚本逻辑：
     * <pre>
     * 输入参数：
     * KEYS[1] = token:{jti}             （String key，查询令牌归属）
     * KEYS[2] = user:tokens:{userId}    （ZSet key，删除用户ZSet中的记录）
     * KEYS[3] = auth:tokens:global:expiry （ZSet key，删除全局ZSet中的记录）
     * 
     * ARGV[1] = userId                  （期望的用户ID）
     * ARGV[2] = jti                     （令牌ID）
     * ARGV[3] = {userId}|{jti}          （组合格式）
     * 
     * 执行步骤：
     * 1. 获取令牌的归属用户：GET token:{jti}
     * 
     * 2. 如果令牌不存在（返回nil）：
     *    - 说明令牌已被删除或过期
     *    - 清理用户ZSet和全局ZSet中的残留记录
     *    - 返回0（验证失败）
     * 
     * 3. 如果令牌存在但归属用户不匹配：
     *    - 说明有人尝试操作别人的令牌
     *    - 返回0（验证失败，但不删除，防止恶意删除）
     * 
     * 4. 如果令牌存在且归属用户匹配：
     *    - 返回1（验证成功）
     * </pre>
     * 
     * <p>为什么验证失败时要清理残留记录？
     * 在极端情况下（如并发删除），String key已过期，但ZSet记录还在。
     * 此时发现残留记录，顺手清理掉，保持数据一致性。
     */
    private static final DefaultRedisScript<Long> VALIDATE_SCRIPT = longScript(
            "local owner = redis.call('GET', KEYS[1])\n" +
            "if not owner then\n" +
            "  redis.call('ZREM', KEYS[2], ARGV[2])\n" +
            "  redis.call('ZREM', KEYS[3], ARGV[3])\n" +
            "  return 0\n" +
            "end\n" +
            "if owner ~= ARGV[1] then return 0 end\n" +
            "return 1"
    );

    /**
     * 删除刷新令牌的Lua脚本
     * 
     * <p>安全地删除令牌，先检查归属关系，防止误删他人令牌。
     * 
     * <p>脚本逻辑：
     * <pre>
     * 输入参数：
     * KEYS[1] = token:{jti}
     * KEYS[2] = user:tokens:{userId}
     * KEYS[3] = auth:tokens:global:expiry
     * 
     * ARGV[1] = userId
     * ARGV[2] = jti
     * ARGV[3] = {userId}|{jti}
     * 
     * 执行步骤：
     * 1. 获取令牌归属：GET token:{jti}
     * 
     * 2. 如果归属存在且不匹配当前用户：
     *    - 返回-1（归属冲突，不执行删除）
     *    - 这可以防止恶意用户删除别人的令牌
     * 
     * 3. 删除3个数据结构中的记录：
     *    - DEL token:{jti}                （删除归属关系）
     *    - ZREM user:tokens:{userId} {jti} （从用户集合移除）
     *    - ZREM auth:tokens:global:expiry {userId}|{jti} （从全局集合移除）
     * 
     * 4. 返回值：
     *    - 1：成功删除（令牌存在）
     *    - 0：令牌不存在（可能已被删除）
     *    - -1：归属冲突（不执行删除）
     * </pre>
     */
    private static final DefaultRedisScript<Long> REMOVE_SCRIPT = longScript(
            "local owner = redis.call('GET', KEYS[1])\n" +
            "if owner and owner ~= ARGV[1] then return -1 end\n" +
            "redis.call('DEL', KEYS[1])\n" +
            "redis.call('ZREM', KEYS[2], ARGV[2])\n" +
            "redis.call('ZREM', KEYS[3], ARGV[3])\n" +
            "if owner then return 1 else return 0 end"
    );

    /**
     * 批量清理过期令牌的Lua脚本
     * 
     * <p>从全局ZSet中查找过期令牌，并清理所有相关数据结构。
     * 这是定时任务使用的核心清理脚本。
     * 
     * <p>脚本逻辑：
     * <pre>
     * 输入参数：
     * KEYS[1] = auth:tokens:global:expiry （全局ZSet，按过期时间排序）
     * 
     * ARGV[1] = nowEpochSeconds           （当前时间戳，用于查找过期令牌）
     * ARGV[2] = batchSize                 （批量处理的数量限制）
     * ARGV[3] = token:                    （String key前缀）
     * ARGV[4] = user:tokens:              （用户ZSet key前缀）
     * ARGV[5] = :tokens                   （用户ZSet key后缀）
     * ARGV[6] = |                         （member分隔符）
     * 
     * 执行步骤：
     * 1. ZRANGEBYSCORE globalZSet -inf {now} LIMIT 0 {batchSize}
     *    - 从全局ZSet中查询所有过期的member（score <= 当前时间）
     *    - 限制每次最多处理batchSize个，避免阻塞Redis
     * 
     * 2. 对每个过期member（格式：{userId}|{jti}）：
     *    a. 解析出userId和jti（用|分隔）
     *    b. DEL token:{jti}                  （删除String key）
     *    c. ZREM user:tokens:{userId} {jti} （从用户ZSet移除）
     *    d. ZREM globalZSet {userId}|{jti}  （从全局ZSet移除）
     * 
     * 3. 返回实际清理的数量
     * </pre>
     * 
     * <p>为什么用ZRANGEBYSCORE？
     * ZSet的score是过期时间戳，通过ZRANGEBYSCORE可以高效地
     * 查询所有过期的令牌（score <= 当前时间），并按批次清理。
     * 这是惰性删除策略的补充，确保过期数据最终会被清理。
     */
    private static final DefaultRedisScript<Long> CLEANUP_BATCH_SCRIPT = longScript(
            "local members = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])\n" +
            "for _, member in ipairs(members) do\n" +
            "  local sep = string.find(member, ARGV[6], 1, true)\n" +
            "  if sep then\n" +
            "    local userId = string.sub(member, 1, sep - 1)\n" +
            "    local jti = string.sub(member, sep + 1)\n" +
            "    redis.call('DEL', ARGV[3] .. jti)\n" +
            "    redis.call('ZREM', ARGV[4] .. userId .. ARGV[5], jti)\n" +
            "  end\n" +
            "  redis.call('ZREM', KEYS[1], member)\n" +
            "end\n" +
            "return #members"
    );

    // ==================== 依赖注入 ====================
    
    /**
     * Redis操作模板（Spring Data Redis提供）
     * 封装了所有Redis操作，如execute、executePipelined等
     */
    private final StringRedisTemplate redisTemplate;

    // ==================== 公开方法 ====================

    /**
     * 保存刷新令牌到Redis（核心方法）
     * 
     * <p>当用户登录或刷新令牌成功后，调用此方法将refreshToken的信息保存到Redis。
     * 这是JWT + Redis混合方案的关键步骤，使refreshToken可以被主动撤销。
     * 
     * <p>业务流程：
     * <pre>
     * 1. 验证参数：userId、jti、expireAt都不能为空
     * 
     * 2. 计算TTL（Time To Live）：
     *    - TTL = 过期时间 - 当前时间（单位：秒）
     *    - 至少为1秒，防止负数导致立即过期
     * 
     * 3. 执行SAVE_SCRIPT Lua脚本，原子性地写入3个数据结构：
     *    a. String: token:{jti} = {userId}，设置TTL
     *    b. ZSet: user:tokens:{userId} 添加 {jti}，score=过期时间
     *    c. ZSet: auth:tokens:global:expiry 添加 {userId}|{jti}，score=过期时间
     * 
     * 4. 检查返回值：
     *    - 如果返回1，保存成功
     *    - 如果返回其他值或null，抛出异常
     * </pre>
     * 
     * <p>与其他方法的配合：
     * <ul>
     *   <li>AuthServiceImpl.login()：登录成功后调用此方法保存refreshToken</li>
     *   <li>AuthServiceImpl.register()：注册成功后调用此方法保存refreshToken</li>
     *   <li>AuthServiceImpl.refreshToken()：刷新令牌成功后调用此方法保存新refreshToken</li>
     *   <li>validateRefreshToken()：保存后可用此方法验证令牌有效性</li>
     *   <li>removeRefreshToken()：用户登出时调用此方法删除令牌</li>
     * </ul>
     * 
     * <p>注意事项：
     * <ul>
     *   <li>同一用户的旧refreshToken会被新令牌覆盖（单点登录）</li>
     *   <li>TTL基于expireAt计算，确保令牌过期后Redis自动清理</li>
     *   <li>如果Redis写入失败，会抛出IllegalStateException，调用方需要处理</li>
     * </ul>
     * 
     * @param userId 用户ID，标识令牌归属
     * @param jti JWT令牌的唯一ID（UUID）
     * @param expireAt 令牌过期时间（绝对时间戳）
     * @throws IllegalArgumentException 如果参数为空
     * @throws IllegalStateException 如果Redis写入失败
     */
    @Override
    public void saveRefreshToken(Long userId, String jti, Instant expireAt ,ClientType clientType) {
        // 步骤1：验证参数
        // 确保必要参数都不为空，防止空指针异常
        requireArgs(userId, jti, expireAt);

        // 步骤2：计算TTL和过期时间戳
        // TTL = 过期时间 - 当前时间（秒），至少为1秒
        // expireAtEpochSeconds用于ZSet的score（按时间排序）
        long ttlSeconds = Math.max(1L, Duration.between(Instant.now(), expireAt).toSeconds());
        long expireAtEpochSeconds = expireAt.getEpochSecond();

        // 步骤3：执行Lua脚本，原子性地写入3个Redis数据结构 + 按客户端类型清理旧令牌
        // 
        // Lua脚本逻辑：
        // 1. 保存新的RT到String（用于验证归属）
        // 2. 添加到User ZSet和Global ZSet
        // 3. TTL管理
        // 4. 【核心】按客户端类型分组统计，每个类型只保留maxPerClient个最新的RT
        //    - 超过限制时，删除同类型中score最小的（最旧的）RT及其String key
        //
        // 参数说明：
        // KEYS[1] = tokenStringKey (新RT的String key)
        // KEYS[2] = userZsetKey (用户维度的ZSet)
        // KEYS[3] = globalZsetKey (全局维度的ZSet)
        // ARGV[1] = userId (用户ID)
        // ARGV[2] = expireAtEpochSeconds (过期时间戳，作为ZSet的score)
        // ARGV[3] = ttlSeconds (TTL)
        // ARGV[4] = newMember ("WEB|new-jti" 格式)
        // ARGV[5] = globalMember ("1001|new-jti" 格式)
        // ARGV[6] = tokenKeyPrefix ("token:1001:slot:" 前缀)
        // ARGV[7] = maxPerClient (每个客户端类型最大保留数，通常为1)
        Long saved = redisTemplate.execute(
                SAVE_SCRIPT,
                Arrays.asList(jtiTokenKey(userId.toString(),jti,clientType), userTokenZsetKey(userId), GLOBAL_EXPIRY_ZSET_KEY),
                String.valueOf(userId),
                String.valueOf(expireAtEpochSeconds),
                String.valueOf(ttlSeconds),
                userSetMember(clientType.getCode(),jti),
                globalMember(userId, jti),
                jwtTokenPrefix(userId.toString()),
                "1"  // 每个客户端类型最多保留1个RT（单点登录）
        );
        // 步骤4：检查执行结果
        // Lua脚本应该返回1，表示保存成功
        // 如果返回null或其他值，说明Redis执行异常
        if (saved == null || saved != 1L) {
            throw new IllegalStateException("保存刷新令牌失败");
        }
    }


    /**
     * 验证刷新令牌是否有效（安全检查）
     * 
     * <p>在用户使用refreshToken刷新令牌或登出前，调用此方法验证令牌是否有效。
     * 这是防止使用已失效令牌的关键检查点。
     * 
     * <p>验证逻辑：
     * <pre>
     * 1. 验证参数：userId、jti不能为空
     * 
     * 2. 执行VALIDATE_SCRIPT Lua脚本：
     *    a. 查询String key：GET token:{jti}
     *    b. 如果key不存在（返回nil）：
     *       - 令牌已被删除或过期
     *       - 清理用户ZSet和全局ZSet中的残留记录
     *       - 返回0（验证失败）
     *    c. 如果key存在但值不匹配（owner != userId）：
     *       - 令牌归属不匹配，可能是恶意操作
     *       - 返回0（验证失败，但不删除）
     *    d. 如果key存在且值匹配（owner == userId）：
     *       - 令牌有效，属于当前用户
     *       - 返回1（验证成功）
     * 
     * 3. 返回验证结果：true或false
     * </pre>
     * 
     * <p>与其他方法的配合：
     * <ul>
     *   <li>AuthServiceImpl.refreshToken()：刷新令牌前调用此方法验证</li>
     *   <li>AuthServiceImpl.logout()：单设备登出前调用此方法验证</li>
     *   <li>AuthServiceImpl.logoutAll()：全设备登出前调用此方法验证</li>
     *   <li>saveRefreshToken()：保存后可用此方法验证是否保存成功</li>
     * </ul>
     * 
     * <p>为什么验证失败时要清理残留记录？
     * 在某些并发场景下，可能出现数据不一致：
     * - String key已过期（TTL到期），但ZSet记录还没被清理
     * - 此时验证发现String key不存在，顺手清理ZSet中的残留
     * 这是一种"懒清理"策略，发现残留就清理。
     * 
     * @param userId 用户ID，用于验证令牌归属
     * @param jti JWT令牌的唯一ID
     * @return true表示令牌有效，false表示令牌无效或已过期
     */
    @Override
    public boolean validateRefreshToken(Long userId, String jti,ClientType clientType) {
        // 步骤1：验证参数
        requireArgs(userId, jti, Instant.now());

        // 步骤2：执行Lua脚本验证令牌
        // 脚本会检查：
        // - 令牌是否存在（String key是否存在）
        // - 令牌是否属于当前用户（value是否等于userId）
        // 如果不存在，还会清理ZSet中的残留记录
        Long result = redisTemplate.execute(
                VALIDATE_SCRIPT,
                Arrays.asList(jtiTokenKey(userId.toString(),jti ,clientType), userTokenZsetKey(userId), GLOBAL_EXPIRY_ZSET_KEY),
                String.valueOf(userId),
                userSetMember(clientType.getCode(),jti),
                globalMember(userId, jti)
        );
        
        // 步骤3：返回验证结果
        // 脚本返回1表示验证成功，其他值表示失败
        return result != null && result == 1L;
    }

    /**
     * 删除指定的刷新令牌（安全删除）
     * 
     * <p>当用户主动登出或令牌过期时，调用此方法从Redis中删除令牌。
     * 删除操作会检查归属关系，防止误删他人令牌。
     * 
     * <p>删除逻辑：
     * <pre>
     * 1. 验证参数：userId、jti不能为空
     * 
     * 2. 执行REMOVE_SCRIPT Lua脚本：
     *    a. 查询令牌归属：GET token:{jti}
     *    b. 如果归属不匹配（owner != userId）：
     *       - 返回-1，不执行删除
     *       - 防止恶意用户删除别人的令牌
     *    c. 如果归属匹配或令牌不存在：
     *       - DEL token:{jti}                  （删除String key）
     *       - ZREM user:tokens:{userId} {jti} （从用户ZSet移除）
     *       - ZREM globalZSet {userId}|{jti}  （从全局ZSet移除）
     *       - 返回1（成功删除）或0（令牌不存在）
     * 
     * 3. 如果返回-1，记录警告日志（归属冲突）
     * </pre>
     * 
     * <p>与其他方法的配合：
     * <ul>
     *   <li>AuthServiceImpl.logout()：用户单设备登出时调用此方法</li>
     *   <li>removeUserAllRefreshToken()：全设备登出时调用批量删除方法</li>
     *   <li>saveRefreshToken()：保存新令牌前可能需要先删除旧令牌</li>
     * </ul>
     * 
     * <p>为什么删除时要检查归属？
     * 防止恶意攻击：
     * 1. 攻击者知道了别人的jti（令牌ID）
     * 2. 攻击者尝试调用删除接口删除别人的令牌
     * 3. 检查归属发现不匹配，拒绝删除
     * 如果没有归属检查，攻击者可以随意删除他人令牌，造成恶意登出。
     * 
     * @param userId 用户ID
     * @param jti JWT令牌的唯一ID
     */
    @Override
    public void removeRefreshToken(Long userId, String jti,ClientType clientType) {
        // 步骤1：验证参数
        requireArgs(userId, jti, Instant.now());

        // 步骤2：执行Lua脚本删除令牌
        // 脚本会检查归属关系，防止误删他人令牌
        // 返回值：1=成功删除，0=令牌不存在，-1=归属冲突
        Long result = redisTemplate.execute(
                REMOVE_SCRIPT,
                Arrays.asList(jtiTokenKey(userId.toString(),jti,clientType), userTokenZsetKey(userId), GLOBAL_EXPIRY_ZSET_KEY),
                String.valueOf(userId),
                userSetMember(clientType.getCode(),jti),
                globalMember(userId, jti)
        );

        // 步骤3：处理归属冲突的情况
        // 如果返回-1，说明令牌存在但不属于当前用户
        // 这可能是恶意操作，记录警告日志以便追踪
        if (result != null && result == -1L) {
            log.warn("删除的不是当前用户所拥有的令牌 - userId: {}, jti: {}", userId, jti);
        }
    }

    /**
     * 删除用户的所有刷新令牌（全设备登出）
     * 
     * <p>当用户执行"全设备登出"操作时（如修改密码、怀疑账号被盗），
     * 需要删除该用户在所有设备上的refreshToken，强制所有设备重新登录。
     * 
     * <p>删除逻辑：
     * <pre>
     * 1. 验证参数：userId不能为空
     * 
     * 2. 查询用户的所有令牌：ZRANGE user:tokens:{userId} 0 -1
     *    - 从用户ZSet中获取所有jti
     *    - 如果ZSet不存在或为空，直接删除ZSet并返回
     * 
     * 3. 使用Pipeline批量删除：
     *    a. 对每个jti：
     *       - DEL token:{jti}              （删除String key）
     *       - ZREM globalZSet {userId}|{jti} （从全局ZSet移除）
     *    b. 删除用户ZSet：DEL user:tokens:{userId}
     * 
     * 4. Pipeline的优势：
     *    - 将所有命令打包一次性发送给Redis
     *    - 减少网络往返次数，提高性能
     *    - 适合批量删除场景
     * </pre>
     * 
     * <p>为什么用Pipeline而不是Lua脚本？
     * 1. 删除逻辑相对简单，不需要Lua的原子性保证
     *    （全设备登出本身就是"破坏性"操作，允许部分失败）
     * 2. Pipeline性能更好（命令打包发送，减少网络延迟）
     * 3. 如果用户有大量令牌（如100个），Lua脚本会阻塞Redis过久
     * 
     * <p>与其他方法的配合：
     * <ul>
     *   <li>AuthServiceImpl.logoutAll()：全设备登出时调用此方法</li>
     *   <li>AuthServiceImpl.bumpTokenVersion()：递增版本号，使旧JWT失效</li>
     *   <li>saveRefreshToken()：删除后用户重新登录时会保存新令牌</li>
     * </ul>
     * 
     * @param userId 用户ID
     */
    @Override
    public void removeUserAllRefreshToken(Long userId,ClientType clientType) {
        // 步骤1：验证参数
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }

        // 步骤2：获取用户的令牌ZSet的key
        String userZsetKey = userTokenZsetKey(userId);

        // 步骤3：查询用户的所有令牌（获取所有jti）
        // ZRANGE userZsetKey 0 -1 返回ZSet中的所有member
        Set<String> allTokens = redisTemplate.opsForZSet().range(userZsetKey, 0, -1);

        // 步骤4：如果用户没有令牌，直接清理并返回
        if (allTokens == null || allTokens.isEmpty()) {
            // 删除空的ZSet（清理垃圾数据）
            redisTemplate.delete(userZsetKey);
            return;
        }

        // 步骤5：使用Pipeline批量删除所有令牌
        // Pipeline将多个命令打包一次性发送，减少网络往返
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            // 预计算key的字节数组，避免循环中重复转换
            byte[] globalKeyBytes = GLOBAL_EXPIRY_ZSET_KEY.getBytes(StandardCharsets.UTF_8);
            byte[] userKeyBytes = userZsetKey.getBytes(StandardCharsets.UTF_8);

            // 对每个jti，删除String key和全局ZSet中的记录
            for (String jti : allTokens) {
                // 删除单个令牌的归属关系
                connection.keyCommands().del(jtiTokenKey(userId.toString(),jti,clientType).getBytes(StandardCharsets.UTF_8));
                // 从全局ZSet中移除该令牌
                connection.zSetCommands().zRem(globalKeyBytes, globalMember(userId, jti).getBytes(StandardCharsets.UTF_8));
            }
            // 删除整个用户ZSet（一次性删除所有令牌）
            connection.keyCommands().del(userKeyBytes);
            return null; // Pipeline要求返回null
        });
    }

    /**
     * 清理指定客户端类型的超额令牌
     *
     * <p>当同一用户在同一客户端类型下有多个刷新令牌时（如多次登录），
     * 此方法会删除最旧的令牌，只保留最新的N个。
     *
     * <p>业务流程：
     * <pre>
     * 1. 查询用户的所有令牌（从User ZSet）
     * 2. 按客户端类型过滤出指定类型的令牌
     * 3. 如果数量超过maxToKeep，删除最旧的（score最小的）
     * 4. 返回实际删除的数量
     * </pre>
     *
     * <p>使用场景：
     * <ul>
     *   <li>用户登录前清理旧令牌，防止令牌堆积</li>
     *   <li>定期任务修复异常数据</li>
     * </ul>
     *
     * @param userId 用户ID
     * @param clientType 客户端类型（WEB、APP等）
     * @param maxToKeep 保留的最大数量（通常为1或3）
     * @return 实际清理的令牌数量
     */
    @Override
    public int cleanupExcessTokens(Long userId, ClientType clientType, int maxToKeep) {
        if (userId == null || maxToKeep <= 0) {
            throw new IllegalArgumentException("userId不能为空且maxToKeep必须大于0");
        }

        String userZsetKey = userTokenZsetKey(userId);

        // 1. 查询该用户的所有令牌（按score升序排列=过期时间升序=最旧的在前）
        Set<String> allTokens = redisTemplate.opsForZSet().range(userZsetKey, 0, -1);

        if (allTokens == null || allTokens.isEmpty()) {
            return 0;
        }

        // 2. 过滤出指定客户端类型的令牌（格式："WEB|jti" 或 "APP|jti"）
        String clientPrefix = clientType.getCode() + "|";
        List<String> clientTokens = allTokens.stream()
                .filter(token -> token.startsWith(clientPrefix))
                .collect(Collectors.toList());

        // 3. 如果数量未超过限制，无需清理
        if (clientTokens.size() <= maxToKeep) {
            return 0;
        }

        // 4. 计算需要删除的数量（保留最新的maxToKeep个，删除其余的）
        int toRemoveCount = clientTokens.size() - maxToKeep;
        log.info("开始清理用户{}的{}客户端超额令牌，当前{}个，保留{}个，需删除{}个",
                userId, clientType.getDescription(), clientTokens.size(), maxToKeep, toRemoveCount);

        // 5. 删除最旧的toRemoveCount个令牌（列表已按score升序，所以前toRemoveCount个就是最旧的）
        for (int i = 0; i < toRemoveCount; i++) {
            String oldTokenMember = clientTokens.get(i);  // 格式："WEB|old-jti"
            String oldJti = oldTokenMember.substring(oldTokenMember.indexOf("|") + 1);

            try {
                // 复用已有的removeRefreshToken方法进行安全删除
                removeRefreshToken(userId, oldJti, clientType);
                log.debug("已删除旧令牌 - 用户ID: {}, JTI: {}, 客户端: {}", userId, oldJti, clientType.getDescription());
            } catch (Exception e) {
                log.warn("删除旧令牌失败（继续清理其他令牌）- 用户ID: {}, JTI: {}, 错误: {}", 
                        userId, oldJti, e.getMessage());
            }
        }

        log.info("完成清理用户{}的{}客户端超额令牌，共删除{}个", userId, clientType.getDescription(), toRemoveCount);
        return toRemoveCount;
    }



    /**
     * 验证方法参数（参数校验）
     * 
     * <p>确保必要参数都不为空，防止空指针异常。
     * 在所有公开方法开始时调用。
     * 
     * @param userId 用户ID
     * @param jti 令牌ID
     * @param instant 过期时间或当前时间
     * @throws IllegalArgumentException 如果任何参数为空
     */
    private static void requireArgs(Long userId, String jti, Instant instant) {
        if (userId == null || !StringUtils.hasText(jti) || instant == null) {
            throw new IllegalArgumentException("userId/jti/expireAt cannot be null");
        }
    }

    /**
     * 构建用户令牌ZSet的key
     * 
     * <p>格式：user:tokens:{userId}
     * 例如：user:tokens:1001
     * 
     * @param userId 用户ID
     * @return 完整的Redis key
     */
    private String userTokenZsetKey(Long userId) {
        return USER_SET_KEY_PREFIX + userId + USER_TOKEN_KEY_SUFFIX;
    }

    /**
     * 构建令牌归属String的key
     * 
     * <p>格式：token:{jti}
     * 例如：token:abc123-uuid-xyz
     * 
     * @param jti JWT令牌的唯一ID
     * @return 完整的Redis key
     */
    private String jtiTokenKey(String userId , String jti , ClientType type) {
        return TOKEN_KEY_PREFIX + userId+ ":" + "slot:" + type.getCode() + ":"  + jti;
    }

    private  String jwtTokenPrefix(String userId){
        return TOKEN_KEY_PREFIX + userId + ":" + "slot:" + ":";
    }

    /**
     * 构建全局ZSet的member格式
     * 
     * <p>格式：{userId}|{jti}
     * 例如：1001|abc123-uuid-xyz
     * 
     * <p>为什么用这种格式？
     * 1. 包含用户ID和令牌ID，清理时可以快速解析
     * 2. 使用|分隔符，避免与数字冲突
     * 3. 保证唯一性（同一用户的不同令牌，不同用户的不同令牌）
     * 
     * @param userId 用户ID
     * @param jti 令牌ID
     * @return 组合格式的member字符串
     */
    private String globalMember(Long userId, String jti) {
        return userId + GLOBAL_MEMBER_SEPARATOR + jti;
    }
    private Object userSetMember(String clientType, String jti) {
        return clientType + GLOBAL_MEMBER_SEPARATOR + jti;
    }
}
