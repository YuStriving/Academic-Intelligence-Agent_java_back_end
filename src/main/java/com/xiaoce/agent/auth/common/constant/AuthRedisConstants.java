package com.xiaoce.agent.auth.common.constant;

import com.xiaoce.agent.auth.enums.ValidateCodeSendSceneEnums;

/**
 * Redis常量定义类（Redis key命名规范）
 * 
 * <p>集中定义auth模块使用的所有Redis key前缀和常量。
 * 统一管理key的命名，避免分散定义导致的不一致问题。
 * 
 * <p>命名规范：
 * <pre>
 * 格式：{模块}:{子模块}:{用途}:{详情}
 * 示例：auth:refresh:user: （认证模块-刷新令牌-用户维度）
 * 
 * 好处：
 * 1. 通过key即可知道用途
 * 2. 方便在Redis客户端中搜索和筛选
 * 3. 避免不同模块的key冲突
 * </pre>
 * 
 * <p>Redis数据结构说明：
 * <pre>
 * 1. String类型：
 *    - auth:refresh:jti:expire:{jti} = {userId}
 *      用途：存储令牌归属关系，验证令牌属于哪个用户
 *      TTL：令牌的剩余有效期
 * 
 * 2. ZSet类型（有序集合）：
 *    - auth:refresh:user:{userId}
 *      Member: {jti}
 *      Score: 过期时间戳
 *      用途：存储某个用户的所有有效令牌
 * 
 *    - auth:refresh:tokens:expiry
 *      Member: {userId}|{jti}
 *      Score: 过期时间戳
 *      用途：全局过期令牌集合，用于定时清理
 * 
 * 3. String类型（数值）：
 *    - auth:user:token:version:{userId} = {version}
 *      用途：存储用户的当前令牌版本号
 * 
 * 4. Bitmap类型（位图）：
 *    - auth:user:is:banned:bitmap
 *      Offset: {userId}
 *      Bit: 1=封禁，0=正常
 *      用途：高效存储用户封禁状态
 * 
 * 5. String类型：
 *    - auth:validate:code:{userId} = {code}
 *      用途：存储验证码（预留功能）
 * </pre>
 * 
 * @author xiaoce
 * @since 1.0
 */
public final class AuthRedisConstants {

    /**
     * 私有构造函数，防止实例化
     * 这是一个纯常量类，不应该被实例化
     */
    private AuthRedisConstants() {
    }

    // ==================== 刷新令牌相关 ====================
    
    /**
     * 用户刷新令牌ZSet的key前缀
     * 
     * <p>完整格式：auth:refresh:user:{userId}
     * 数据类型：ZSet（有序集合）
     * Member：令牌的jti（JWT ID）
     * Score：令牌的过期时间戳
     * 
     * <p>用途：
     * - 快速查询某个用户有多少有效令牌
     * - 支持单点登录（一个用户只有一个有效令牌）
     * - 支持全设备登出（删除用户所有令牌）
     * 
     * <p>示例：
     * Key: auth:refresh:user:1001
     * Members: {abc123-uuid, def456-uuid}
     * Scores: {1700000000, 1700003600}
     */
    public static final String USER_SET_KEY_PREFIX = "auth:refresh:user:";

    /**
     * 令牌过期标记的key前缀
     * 
     * <p>完整格式：auth:refresh:jti:expire:{jti}
     * 数据类型：String
     * Value：用户ID
     * TTL：令牌的剩余有效期
     * 
     * <p>用途：
     * - 验证令牌是否存在（是否已登出或过期）
     * - 验证令牌归属关系（防止越权操作）
     * - TTL到期后自动删除，无需手动清理
     * 
     * <p>示例：
     * Key: auth:refresh:jti:expire:abc123-uuid
     * Value: 1001
     * TTL: 599秒（剩余10分钟过期）
     */
    public static final String TOKEN_KEY_PREFIX = "auth:refresh:jti:expire:";

    /**
     * 用户令牌版本号的key前缀
     * 
     * <p>完整格式：auth:user:token:version:{userId}
     * 数据类型：String
     * Value：版本号（数字字符串）
     * 
     * <p>用途：
     * - 实现全设备强制登出功能
     * - 用户修改密码或全设备登出时，版本号+1
     * - JWT中包含签发时的版本号
     * - 验证时对比版本号，不匹配则令牌失效
     * 
     * <p>工作原理：
     * 1. 用户登录：JWT写入版本号0
     * 2. 用户修改密码：Redis版本号变为1
     * 3. 旧JWT验证：0 != 1，验证失败
     * 4. 新JWT写入版本号1
     * 
     * <p>示例：
     * Key: auth:user:token:version:1001
     * Value: "3"（已执行过3次强制登出）
     */
    public static final String USER_TOKEN_VERSION_KEY_PREFIX = "auth:user:token:version:";

    /**
     * 全局过期令牌ZSet的key
     * 
     * <p>完整格式：auth:refresh:tokens:expiry（固定key，不需要拼接）
     * 数据类型：ZSet（有序集合）
     * Member：{userId}|{jti}（用户ID和令牌ID的组合）
     * Score：令牌的过期时间戳
     * 
     * <p>用途：
     * - 定时任务批量清理所有用户的过期令牌
     * - ZSet按过期时间排序，可高效查询过期令牌
     * - 分批清理，避免阻塞Redis
     * 
     * <p>为什么用全局ZSet？
     * 如果只按用户维度存储，定时清理需要遍历所有用户，效率低。
     * 全局ZSet可以一次性查询所有过期令牌，按批次清理。
     * 
     * <p>示例：
     * Key: auth:refresh:tokens:expiry
     * Members: {1001|abc123, 1002|def456, 1001|ghi789}
     * Scores: {1700000000, 1700000100, 1700000200}
     */
    public static final String GLOBAL_EXPIRY_ZSET_KEY = "auth:refresh:tokens:expiry";

    // ==================== 用户状态相关 ====================
    
    /**
     * 用户封禁状态Bitmap的key
     * 
     * <p>完整格式：auth:user:is:banned:bitmap（固定key）
     * 数据类型：Bitmap（位图）
     * Offset：用户ID
     * Bit：1=已封禁，0=正常
     * 
     * <p>为什么用Bitmap？
     * <pre>
     * 空间效率极高：
     * - 1亿用户只需 1亿bit = 12.5MB
     * - 如果用String：1亿个key，每个key约100字节 = 10GB
     * - 空间节省约800倍
     * 
     * 查询效率极高：
     * - O(1)时间复杂度
     * - 单个位操作，非常快
     * </pre>
     * 
     * <p>使用场景：
     * - 管理员封禁用户：SETBIT key userId 1
     * - 用户登录时检查：GETBIT key userId
     * - 如果返回1，拒绝登录
     * 
     * <p>示例：
     * Key: auth:user:is:banned:bitmap
     * Offset 1001: 1（用户1001被封禁）
     * Offset 1002: 0（用户1002正常）
     */
    public static final String USER_IS_BANNED_BITMAP_KEY = "auth:user:is:banned:bitmap";

    // ==================== 验证码相关（预留） ====================
    
    /**
     * 用户验证码的key前缀
     * 
     * <p>完整格式：auth:validate:code:{userId}
     * 数据类型：Hash
     * Value：验证码
     * TTL：验证码有效期（通常5-10分钟）
     * 
     * <p>用途：
     * - 用户注册或修改密码时发送验证码
     * - 验证码存储到Redis，设置TTL自动过期
     * - 用户输入验证码时，与Redis中的对比
     * 
     * 使用hash结构，file：code，attempt，maxAttempt
     */
    public static final String USER_VALIDATE_CODE_KEY_PREFIX = "auth:validate:code:";

    // hash的filed 的值
    public static final String ALREADY_ATTEMPT_COUNT = "attempts";

    public static final String MAX_ATTEMPT_COUNT = "maxAttempts";

    public static final String VALIDATE_CODE = "code";

    public static String buildKeyAboutValidateCodeHash(String email, ValidateCodeSendSceneEnums sceneEnums){
        return USER_VALIDATE_CODE_KEY_PREFIX + email + ":" + sceneEnums;
    }

    // 用来存储上一次验证码，也就是最后一次验证码发送的键，用来判断是否达到了发送间隔
    public static final String USER_VALIDATE_CODE_LAST_KEY_PREFIX = "auth:validate:code:last:";

    public static String buildKeyAboutValidateLastCode(String email , ValidateCodeSendSceneEnums sceneEnums){
        return USER_VALIDATE_CODE_LAST_KEY_PREFIX + email + ":" + sceneEnums;
    }

    // 用来判断当前用户今天发送了几次验证码
    public static final String USER_VALIDATE_CODE_SEND_COUNT = "auth:validate:code:send:count:";

    public static String buildKeyAboutValidateSendCount(String email, ValidateCodeSendSceneEnums sceneEnums , String date){
        return USER_VALIDATE_CODE_SEND_COUNT + email + ":" + sceneEnums + ":" + date;
    }


}
