package com.xiaoce.agent.auth.service.impl;

import com.xiaoce.agent.auth.common.exception.BusinessException;
import com.xiaoce.agent.auth.common.exception.ErrorCode;
import com.xiaoce.agent.auth.config.AuthProperties;
import com.xiaoce.agent.auth.domain.dto.JwtUserInfo;
import com.xiaoce.agent.auth.domain.po.User;
import com.xiaoce.agent.auth.domain.vo.TokenPair;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * JWT令牌服务（核心安全组件）
 * 
 * <p>这个类负责所有JWT（JSON Web Token）相关的操作，是整个认证系统的核心。
 * 你可以把JWT想象成用户的"数字身份证"，包含用户信息和有效期，带有防伪签名。
 * 
 * <p>核心功能：
 * <ul>
 *   <li>生成JWT令牌：为用户创建访问令牌和刷新令牌</li>
 *   <li>验证JWT令牌：检查令牌是否被篡改、是否过期</li>
 *   <li>提取用户信息：从令牌中解析出用户ID等身份信息</li>
 *   <li>版本控制：支持令牌版本管理，实现全设备强制登出</li>
 * </ul>
 * 
 * <p>工作原理：
 * <pre>
 * 1. 用户登录成功后，调用issueTokenPair()生成一对令牌：
 *    - accessToken（访问令牌）：短期有效（如15分钟），用于日常接口访问
 *    - refreshToken（刷新令牌）：长期有效（如7天），用于获取新的令牌对
 * 
 * 2. 用户访问受保护接口时，携带accessToken：
 *    - 系统调用parseAndVerify()验证令牌有效性
 *    - 调用extractUserInfo()获取当前用户信息
 * 
 * 3. accessToken过期后，用户使用refreshToken调用刷新接口：
 *    - 系统验证refreshToken有效性
 *    - 调用issueTokenPair()生成新的令牌对
 * 
 * 4. 用户登出或修改密码时：
 *    - 通过令牌版本号机制使旧令牌失效
 *    - 调用assertTokenVersion()验证令牌版本
 * </pre>
 * 
 * <p>与其他组件的配合：
 * <ul>
 *   <li>AuthServiceImpl：调用本服务生成和验证令牌</li>
 *   <li>JwtAuthenticationConverter：调用本服务验证accessToken并转换为用户认证信息</li>
 *   <li>RefreshTokenStoreImpl：配合Redis存储refreshToken，实现令牌状态管理</li>
 *   <li>AuthProperties：从配置文件读取JWT相关参数（有效期、发行者等）</li>
 * </ul>
 * 
 * <p>注意事项：
 * <ul>
 *   <li>JWT一旦签发就无法作废（除非使用版本号机制），因此accessToken有效期不宜过长</li>
 *   <li>所有敏感操作（如支付、修改密码）应该重新验证用户身份，不能仅依赖JWT</li>
 *   <li>令牌版本号是实现强制登出的关键，修改密码时必须递增版本号</li>
 * </ul>
 * 
 * @author xiaoce
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    // ==================== 常量定义 ====================
    
    /**
     * JWT声明名称：令牌类型（用于区分access和refresh令牌）
     * 
     * <p>在JWT的payload中，我们用这个字段标识令牌用途：
     * - "access"：访问令牌，用于接口鉴权
     * - "refresh"：刷新令牌，用于获取新的令牌对
     */
    public static final String CLAIM_TOKEN_TYPE = "token_type";
    
    /**
     * 访问令牌类型标识值
     */
    public static final String TYPE_ACCESS = "access";
    
    /**
     * 刷新令牌类型标识值
     */
    public static final String TYPE_REFRESH = "refresh";
    
    /**
     * JWT声明名称：用户ID（存储在payload中，方便快速提取）
     */
    public static final String CLAIM_USER_ID = "uid";
    
    /**
     * JWT声明名称：令牌版本号（用于实现全设备强制登出功能）
     * 
     * <p>工作原理：
     * 1. 每个用户有一个令牌版本号，存储在Redis中
     * 2. 每次签发JWT时，将当前版本号写入JWT的payload
     * 3. 验证JWT时，对比JWT中的版本号和Redis中的版本号
     * 4. 如果版本号不匹配，说明用户已强制登出，JWT失效
     * 
     * <p>使用场景：
     * - 用户修改密码后，递增版本号，所有旧设备上的JWT都会失效
     * - 用户账号被盗，管理员可以强制该用户所有设备登出
     */
    public static final String CLAIM_TOKEN_VERSION = "tv";

    // ==================== 依赖注入 ====================
    
    /**
     * 认证配置属性（从application.yml读取）
     * 包含JWT有效期、发行者等配置参数
     */
    private final AuthProperties authProperties;
    
    /**
     * JWT编码器（用于签名和生成JWT令牌）
     * 使用RSA非对称加密，私钥用于签名，公钥用于验证
     */
    private final JwtEncoder jwtEncoder;
    
    /**
     * JWT解码器（用于验证和解析JWT令牌）
     * 使用RSA公钥验证签名，确保令牌未被篡改
     */
    private final JwtDecoder jwtDecoder;

    /**
     * UTC时钟（用于生成标准时间戳）
     * 使用UTC时间避免时区问题，确保分布式系统时间一致
     */
    private final Clock clock = Clock.systemUTC();
    private final RefreshTokenStoreImpl refreshTokenStore;

    // ==================== 公开方法 ====================

    /**
     * 生成JWT令牌对（核心方法）
     * 
     * <p>这是整个认证系统最重要的方法之一。当用户成功登录或注册后，
     * 系统调用此方法为用户生成一对JWT令牌：
     * 
     * <p>业务流程：
     * <pre>
     * 1. 获取当前时间，计算两个过期时间：
     *    - accessToken过期时间 = 当前时间 + accessToken有效期（如15分钟）
     *    - refreshToken过期时间 = 当前时间 + refreshToken有效期（如7天）
     * 
     * 2. 生成两个唯一的UUID作为令牌ID（jti）：
     *    - accessTokenId：访问令牌的唯一标识
     *    - refreshTokenId：刷新令牌的唯一标识
     * 
     * 3. 调用createAccessToken()创建访问令牌：
     *    - 包含用户ID、用户名、昵称等身份信息
     *    - 包含令牌类型（"access"）
     *    - 包含令牌版本号（用于强制登出）
     *    - 使用RSA私钥签名
     * 
     * 4. 调用createRefreshToken()创建刷新令牌：
     *    - 包含用户ID等基本信息
     *    - 包含令牌类型（"refresh"）
     *    - 包含令牌版本号
     *    - 使用RSA私钥签名
     * 
     * 5. 封装成TokenPair对象返回：
     *    - accessToken：访问令牌字符串
     *    - accessExpiresAt：访问令牌过期时间
     *    - refreshToken：刷新令牌字符串
     *    - refreshExpiresAt：刷新令牌过期时间
     *    - refreshTokenId：刷新令牌ID（用于Redis存储）
     * </pre>
     * 
     * <p>与其他方法的配合：
     * <ul>
     *   <li>AuthServiceImpl.register()：注册成功后调用此方法立即登录</li>
     *   <li>AuthServiceImpl.login()：登录成功后调用此方法生成令牌</li>
     *   <li>AuthServiceImpl.refreshToken()：刷新令牌成功后调用此方法生成新令牌对</li>
     *   <li>RefreshTokenStoreImpl.storeRefreshToken()：使用返回的refreshTokenId存储到Redis</li>
     * </ul>
     * 
     * <p>注意事项：
     * <ul>
     *   <li>每次调用都会生成全新的令牌，即使传入相同的用户信息</li>
     *   <li>令牌版本号必须正确传入，否则会导致强制登出功能失效</li>
     *   <li>生成的令牌无法撤销，只能通过版本号机制使其失效</li>
     * </ul>
     * 
     * @param user 用户实体对象，包含用户ID、用户名、昵称等信息
     * @param tokenVersion 当前令牌版本号，用于实现全设备强制登出
     * @return TokenPair对象，包含访问令牌和刷新令牌及其过期时间
     */
    public TokenPair issueTokenPair(User user, long tokenVersion) {
        // 步骤1：获取当前时间（UTC），计算两个令牌的过期时间
        // issueAt：令牌签发时间
        // accessExpiresAt：访问令牌过期时间（通常15-30分钟）
        // refreshExpiresAt：刷新令牌过期时间（通常7-30天）
        Instant issueAt = Instant.now(clock);
        Instant accessExpiresAt = issueAt.plus(authProperties.getJwt().getAccessTokenTtl());
        Instant refreshExpiresAt = issueAt.plus(authProperties.getJwt().getRefreshTokenTtl());

        // 步骤2：生成两个唯一的UUID作为令牌ID（JWT ID）
        // 每个令牌实例都有唯一的jti，用于标识和追踪令牌
        // 即使同一用户同时登录多次，每个令牌也有不同的jti
        String accessTokenId = UUID.randomUUID().toString();
        String refreshTokenId = UUID.randomUUID().toString();

        // 步骤3：分别创建访问令牌和刷新令牌
        // 两个令牌的区别：
        // - 访问令牌：包含更多用户信息（昵称、用户名），有效期短
        // - 刷新令牌：只包含基本信息，有效期长
        // 两者都包含：用户ID、令牌类型、令牌版本号
        String accessToken = createAccessToken(accessTokenId, user, issueAt, accessExpiresAt, tokenVersion);
        String refreshToken = createRefreshToken(refreshTokenId, user, issueAt, refreshExpiresAt, tokenVersion);

        // 记录调试日志（生产环境通常不打印，避免敏感信息泄露）
        log.debug("JWT令牌对生成成功 - 用户ID: {}, 访问令牌ID: {}, 刷新令牌ID: {}", 
                user.getId(), accessTokenId, refreshTokenId);

        // 步骤4：封装成TokenPair对象返回
        // TokenPair是数据传输对象，包含：
        // - accessToken：访问令牌字符串（客户端用于请求受保护接口）
        // - accessExpiresAt：访问令牌过期时间（客户端可用于提前刷新）
        // - refreshToken：刷新令牌字符串（客户端用于获取新的令牌对）
        // - refreshExpiresAt：刷新令牌过期时间
        // - refreshTokenId：刷新令牌ID（服务端用于Redis存储和验证）
        return new TokenPair(accessToken, accessExpiresAt, refreshToken, refreshExpiresAt, refreshTokenId);
    }

    /**
     * 解析并验证JWT令牌（安全网关）
     * 
     * <p>这个方法是JWT令牌的"安检门"，所有令牌在使用前都必须通过此方法的验证。
     * 它会进行多重检查，确保令牌是合法的、未被篡改的、且未过期。
     * 
     * <p>验证流程：
     * <pre>
     * 1. 调用jwtDecoder.decode()进行基础验证：
     *    - 验证JWT格式是否正确
     *    - 验证RSA签名是否匹配（防止伪造）
     *    - 验证令牌是否过期
     *    - 如果任何一步失败，抛出JwtException
     * 
     * 2. 调用validateCommonClaims()进行业务验证：
     *    - 验证令牌类型（token_type）是否匹配预期
     *      * 防止用refreshToken访问需要accessToken的接口
     *      * 防止用accessToken刷新令牌
     *    - 验证发行者（issuer）是否匹配
     *      * 防止其他系统的令牌混入
     *    - 验证用户ID（subject）是否有效
     *      * 确保subject不为空且是有效的数字
     * 
     * 3. 返回验证通过的JWT对象，供后续使用
     * </pre>
     * 
     * <p>与其他方法的配合：
     * <ul>
     *   <li>JwtAuthenticationConverter：用户请求时调用此方法验证accessToken</li>
     *   <li>AuthServiceImpl.refreshToken()：刷新令牌时调用此方法验证refreshToken</li>
     *   <li>AuthServiceImpl.logout() / logoutAll()：登出时调用此方法验证refreshToken</li>
     *   <li>extractUserInfo()：验证通过后调用此方法提取用户信息</li>
     *   <li>assertTokenVersion()：验证通过后调用此方法检查令牌版本</li>
     * </ul>
     * 
     * <p>异常处理：
     * <ul>
     *   <li>BusinessException：业务验证失败（令牌类型不匹配、发行者错误等）</li>
     *   <li>JwtException：JWT格式错误、签名验证失败、令牌过期等</li>
     *   <li>所有异常都会被转换为统一的BusinessException，方便全局异常处理</li>
     * </ul>
     * 
     * @param token JWT令牌字符串（通常从HTTP请求头Authorization: Bearer {token}获取）
     * @param expectedType 预期的令牌类型（TYPE_ACCESS或TYPE_REFRESH）
     * @return 验证通过的JWT对象，包含所有claims信息
     * @throws BusinessException 如果令牌无效、过期、类型不匹配等
     */
    public Jwt parseAndVerify(String token, String expectedType) {
        try {
            // 步骤1：基础JWT验证
            // jwtDecoder会自动验证：
            // - JWT格式是否正确（三段式：header.payload.signature）
            // - RSA签名是否匹配（使用公钥验证，确保令牌由我们的私钥签发）
            // - 令牌是否过期（检查exp声明）
            // 如果任何验证失败，会抛出JwtException异常
            Jwt jwt = jwtDecoder.decode(token);
            
            // 步骤2：业务规则验证
            // 检查令牌的业务声明是否符合预期：
            // - 令牌类型是否正确（防止access和refresh混用）
            // - 发行者是否匹配（防止其他系统的令牌）
            // - 用户ID是否有效（防止恶意构造的令牌）
            validateCommonClaims(jwt, expectedType);
            
            // 步骤3：返回验证通过的JWT对象
            // 后续可以从此对象中提取用户信息、令牌版本等数据
            return jwt;
        } catch (BusinessException e) {
            // 业务异常直接抛出，保持原有错误信息
            throw e;
        } catch (Exception e) {
            // 其他异常（如JwtException）转换为统一的业务异常
            // 这样可以避免暴露底层技术细节给调用方
            log.debug("JWT令牌验证失败 - 类型: {}, 错误: {}", expectedType, e.getMessage());
            throw invalidTokenError(expectedType);
        }
    }

    /**
     * 从JWT令牌中提取用户信息（身份解析器）
     * 
     * <p>这个方法负责从已验证的JWT令牌中提取用户的身份信息。
     * 当用户访问受保护接口时，系统需要先验证令牌，然后提取用户信息
     * 来确定"谁在访问接口"。
     * 
     * <p>提取的信息：
     * <ul>
     *   <li>userId：用户ID，从JWT的subject声明解析，是用户的唯一标识</li>
     *   <li>displayName：显示名称，优先使用nickName，如果没有则使用username</li>
     * </ul>
     * 
     * <p>与其他方法的配合：
     * <ul>
     *   <li>parseAndVerify()：必须先调用此方法验证令牌，然后才能提取信息</li>
     *   <li>JwtAuthenticationConverter：在请求拦截时调用此方法获取当前用户</li>
     *   <li>业务Service：需要知道当前操作者身份时调用此方法</li>
     * </ul>
     * 
     * <p>使用示例：
     * <pre>
     * // 1. 从请求头获取accessToken
     * String token = request.getHeader("Authorization").substring(7);
     * 
     * // 2. 验证令牌
     * Jwt jwt = jwtTokenService.parseAndVerify(token, JwtTokenService.TYPE_ACCESS);
     * 
     * // 3. 提取用户信息
     * JwtUserInfo userInfo = jwtTokenService.extractUserInfo(jwt);
     * Long userId = userInfo.getUserId(); // 当前用户ID
     * String name = userInfo.getDisplayName(); // 当前用户显示名称
     * </pre>
     * 
     * @param jwt 已验证的JWT令牌对象（必须先通过parseAndVerify验证）
     * @return JwtUserInfo对象，包含用户ID和显示名称
     */
    public JwtUserInfo extractUserInfo(Jwt jwt) {
        // 步骤1：从JWT的subject声明解析用户ID
        // subject是JWT标准声明，通常用于存储主体标识
        // 在我们的系统中，subject存储的是用户ID的字符串形式
        Long userId = parseSubjectAsUserId(jwt.getSubject());
        
        // 步骤2：提取用户显示名称
        // 优先使用nickName（用户自定义昵称）
        // 如果没有nickName，则使用username（登录用户名）作为备选
        String nickName = jwt.getClaimAsString("nickName");
        String username = jwt.getClaimAsString("username");
        
        // 步骤3：构建用户信息对象返回
        // nickName不为空时使用nickName，否则使用username
        return new JwtUserInfo(userId, nickName != null ? nickName : username);
    }

    /**
     * 从JWT令牌中提取令牌ID（JWT ID）
     * 
     * <p>JWT的jti（JWT ID）声明是令牌的唯一标识，每次生成令牌时
     * 都会创建一个UUID作为jti。这个ID用于：
     * 
     * <ul>
     *   <li>在Redis中标识和存储refreshToken</li>
     *   <li>验证refreshToken是否在Redis中存在</li>
     *   <li>实现单点登录（一个用户只能有一个有效的refreshToken）</li>
     *   <li>登出时从Redis中删除对应的refreshToken</li>
     * </ul>
     * 
     * <p>与其他方法的配合：
     * <ul>
     *   <li>issueTokenPair()：生成令牌时创建唯一的jti</li>
     *   <li>RefreshTokenStoreImpl：使用jti作为Redis中存储的key</li>
     *   <li>AuthServiceImpl：登出时使用jti从Redis中删除令牌</li>
     * </ul>
     * 
     * @param jwt JWT令牌对象
     * @return 令牌ID（UUID字符串）
     */
    public String extractJwtId(Jwt jwt) {
        return jwt.getId();
    }

    /**
     * 从JWT令牌中提取令牌版本号（版本控制器）
     * 
     * <p>令牌版本号是实现"全设备强制登出"功能的核心机制。
     * 它的工作原理类似于"锁的版本"：
     * 
     * <p>工作原理：
     * <pre>
     * 1. 每个用户在Redis中有一个当前的令牌版本号（初始为0）
     * 
     * 2. 用户登录时：
     *    - 读取Redis中的当前版本号（如0）
     *    - 将此版本号写入JWT的payload（tv: 0）
     *    - 用户持有JWT去访问接口
     * 
     * 3. 用户访问接口时：
     *    - 从JWT中提取版本号（如0）
     *    - 从Redis读取用户的当前版本号（如0）
     *    - 对比两个版本号：0 == 0，验证通过
     * 
     * 4. 用户修改密码或强制登出时：
     *    - 将Redis中的版本号递增（0 -> 1）
     *    - 用户旧的JWT中的版本号仍然是0
     *    - 再次访问接口时：0 != 1，验证失败，需要重新登录
     * 
     * 5. 用户重新登录时：
     *    - 读取Redis中的新版本号（1）
     *    - 将新版本号写入新JWT（tv: 1）
     *    - 新JWT可以正常访问接口
     * </pre>
     * 
     * <p>注意事项：
     * <ul>
     *   <li>旧令牌可能没有版本号（历史数据），默认返回0</li>
     *   <li>版本号必须是数字，否则会抛出异常</li>
     *   <li>版本号只增不减，防止版本回退攻击</li>
     * </ul>
     * 
     * @param jwt JWT令牌对象
     * @return 令牌版本号（长整型数字）
     * @throws BusinessException 如果版本号格式无效
     */
    public long extractTokenVersion(Jwt jwt) {
        // 步骤1：从JWT的claims中获取版本号原始值
        // tv（token version）是我们自定义的声明名称
        Object raw = jwt.getClaims().get(CLAIM_TOKEN_VERSION);
        
        // 步骤2：处理版本号不存在的情况
        // 旧的令牌可能没有版本号，默认返回0
        // 这样可以保证历史令牌的向后兼容性
        if (raw == null) {
            return 0L;
        }
        
        // 步骤3：处理不同类型的版本号
        // JWT的claims值可能是Number或String类型，需要分别处理
        if (raw instanceof Number number) {
            // 如果是数字类型，直接转换为long
            return number.longValue();
        }
        if (raw instanceof String str) {
            // 如果是字符串类型，尝试解析为数字
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                // 字符串无法解析为数字，说明令牌被恶意篡改
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid token version");
            }
        }
        
        // 其他类型（如布尔值、数组等）都是非法的
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid token version");
    }

    /**
     * 验证令牌版本是否有效（强制登出检查点）
     * 
     * <p>这个方法实现"全设备强制登出"的核心验证逻辑。
     * 它会对比JWT中的版本号和Redis中的当前版本号，
     * 如果不匹配，说明用户已执行过强制登出，当前令牌已失效。
     * 
     * <p>使用场景：
     * <pre>
     * 场景1：用户修改密码
     * 1. 用户在设备A修改密码
     * 2. 系统将Redis中的版本号递增（如0 -> 1）
     * 3. 用户在设备B的旧JWT中的版本号仍是0
     * 4. 设备B访问接口时，调用此方法验证：0 != 1，验证失败
     * 5. 设备B需要重新登录才能继续使用
     * 
     * 场景2：管理员封禁账号
     * 1. 管理员在后台封禁用户账号
     * 2. 系统将Redis中的版本号递增
     * 3. 用户所有设备的JWT都会因为版本号不匹配而失效
     * 
     * 场景3：用户怀疑账号被盗
     * 1. 用户调用"全设备登出"接口
     * 2. 系统将Redis中的版本号递增
     * 3. 盗号者的JWT立即失效
     * </pre>
     * 
     * <p>与其他方法的配合：
     * <ul>
     *   <li>parseAndVerify()：验证令牌签名和过期后调用此方法</li>
     *   <li>AuthServiceImpl.getCurrentTokenVersion()：从Redis获取当前版本号</li>
     *   <li>AuthServiceImpl.bumpTokenVersion()：递增版本号，使旧令牌失效</li>
     *   <li>JwtAuthenticationConverter：每次请求时验证令牌版本</li>
     * </ul>
     * 
     * @param jwt JWT令牌对象（已验证签名和过期）
     * @param currentVersion 当前有效的令牌版本号（从Redis获取）
     * @param expectedType 令牌类型（用于错误提示）
     * @throws BusinessException 如果版本号不匹配
     */
    public void assertTokenVersion(Jwt jwt, long currentVersion, String expectedType) {
        // 步骤1：从JWT中提取版本号
        // 这是令牌签发时写入的版本号，代表令牌"出生时"的版本
        long tokenVersion = extractTokenVersion(jwt);
        
        // 步骤2：对比版本号
        // 如果JWT中的版本号与Redis中的当前版本号不一致，
        // 说明用户在令牌签发后执行过强制登出（如修改密码、全设备登出）
        // 当前令牌应该被视为已失效
        if (tokenVersion != currentVersion) {
            log.debug("JWT令牌版本不匹配 - 期望: {}, 实际: {}, 类型: {}", 
                    currentVersion, tokenVersion, expectedType);
            // 抛出与令牌类型对应的异常
            // access令牌 -> UNAUTHORIZED
            // refresh令牌 -> INVALID_REFRESH_TOKEN
            throw invalidTokenError(expectedType);
        }
        
        // 如果版本号匹配，什么也不做（方法正常返回）
        // 这表示令牌版本有效，可以继续使用
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 解析令牌subject为用户ID（身份解析助手）
     * 
     * <p>JWT的subject声明是标准字段，用于存储令牌的主体标识。
     * 在我们的系统中，subject存储的是用户ID的字符串形式。
     * 此方法负责将字符串转换回Long类型，并验证其合法性。
     * 
     * <p>验证规则：
     * <ul>
     *   <li>subject不能为空或空白</li>
     *   <li>subject必须是有效的数字</li>
     *   <li>用户ID必须大于0（负数或0都是非法的）</li>
     * </ul>
     * 
     * <p>为什么需要验证？
     * 防止恶意用户伪造JWT，在subject中注入非法值（如空字符串、负数等），
     * 导致后续业务逻辑出现异常或安全漏洞。
     * 
     * @param subject JWT的subject声明值（字符串形式的用户ID）
     * @return 用户ID（Long类型）
     * @throws BusinessException 如果subject无效
     */
    public Long parseSubjectAsUserId(String subject) {
        // 步骤1：检查subject是否为空
        // 防止空指针异常和恶意构造的空值
        if (subject == null || subject.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid token subject");
        }
        try {
            // 步骤2：将字符串解析为Long类型
            long userId = Long.parseLong(subject);
            
            // 步骤3：验证用户ID的合法性
            // 用户ID必须是正数（数据库自增ID从1开始）
            // 负数或0都是非法的，可能是恶意构造的令牌
            if (userId <= 0) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid token subject");
            }
            return userId;
        } catch (NumberFormatException e) {
            // subject不是有效的数字格式，说明令牌可能被篡改
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid token subject");
        }
    }

    /**
     * 创建访问令牌（核心生成逻辑）
     * 
     * <p>此方法负责构建访问令牌的完整数据结构并签名。
     * 访问令牌包含丰富的用户信息，用于日常接口鉴权。
     * 
     * <p>JWT结构：
     * <pre>
     * Header（头部）:
     * {
     *   "alg": "RS256",        // 签名算法：RSA with SHA-256
     *   "typ": "JWT"           // 令牌类型
     * }
     * 
     * Payload（载荷）:
     * {
     *   "iss": "your-issuer",  // 发行者（标识此JWT由哪个系统签发）
     *   "iat": 1234567890,     // 签发时间（Issued At）
     *   "exp": 1234568790,     // 过期时间（Expiration）
     *   "jti": "uuid-xxx",     // JWT ID（唯一标识此令牌实例）
     *   "sub": "123",          // 主体（用户ID的字符串形式）
     *   "nickName": "xxx",     // 用户昵称（方便前端显示）
     *   "username": "xxx",     // 用户名（登录账号）
     *   "uid": 123,            // 用户ID（数值类型，方便业务逻辑使用）
     *   "token_type": "access",// 令牌类型（区分access和refresh）
     *   "tv": 0                // 令牌版本号（token version）
     * }
     * 
     * Signature（签名）:
     * RS256(Header, Payload, PrivateKey)
     * </pre>
     * 
     * <p>为什么访问令牌包含这么多信息？
     * 访问令牌用于日常接口访问，包含用户名和昵称可以避免
     * 每次请求都查询数据库获取用户信息，提高性能。
     * 
     * @param tokenId 令牌唯一标识（UUID）
     * @param user 用户实体对象
     * @param issueAt 签发时间
     * @param expireAt 过期时间
     * @param tokenVersion 令牌版本号
     * @return 签名后的JWT访问令牌字符串
     */
    private String createAccessToken(String tokenId, User user, Instant issueAt, Instant expireAt, long tokenVersion) {
        // 构建JWT的Claims（声明/载荷）
        // 使用Builder模式逐个设置JWT的各个字段
        JwtClaimsSet claims = JwtClaimsSet.builder()
                // 标准声明（JWT RFC 7519定义的字段）
                .issuer(authProperties.getJwt().getIssuer())    // 发行者：标识此JWT的来源系统
                .issuedAt(issueAt)                              // 签发时间：JWT创建的时间点
                .expiresAt(expireAt)                            // 过期时间：JWT失效的时间点
                .id(tokenId)                                    // JWT ID：唯一标识此令牌实例
                .subject(String.valueOf(user.getId()))          // 主体：用户ID的字符串形式
                
                // 自定义声明（我们系统特有的字段）
                .claim("nickName", user.getNickname())          // 用户昵称：方便前端显示，避免额外查询
                .claim("username", user.getUsername())          // 用户名：登录账号，用于日志记录等
                .claim(CLAIM_USER_ID, user.getId())             // 用户ID：数值类型，方便业务逻辑直接使用
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)           // 令牌类型：标识这是访问令牌
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)       // 令牌版本号：用于强制登出功能
                .build();
        
        // 使用JwtEncoder对Claims进行签名
        // 内部流程：
        // 1. 使用RSA私钥对Header和Payload进行签名
        // 2. 将Header、Payload、Signature组合成完整的JWT字符串
        // 3. 返回格式：base64(header).base64(payload).base64(signature)
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * 创建刷新令牌（核心生成逻辑）
     * 
     * <p>此方法负责构建刷新令牌的完整数据结构并签名。
     * 刷新令牌只包含最基本的信息，因为它只用于获取新的令牌对，
     * 不用于日常接口访问。
     * 
     * <p>刷新令牌与访问令牌的区别：
     * <ul>
     *   <li>刷新令牌不包含nickName和username（不需要显示用户信息）</li>
     *   <li>刷新令牌有效期更长（通常7-30天 vs 15-30分钟）</li>
     *   <li>刷新令牌会存储到Redis中，可以被主动撤销</li>
     *   <li>刷新令牌的token_type是"refresh"（防止混用）</li>
     * </ul>
     * 
     * <p>为什么刷新令牌要存储到Redis？
     * JWT本身无法撤销，但刷新令牌存储到Redis后，
     * 我们可以通过删除Redis中的记录来实现"撤销刷新令牌"的功能。
     * 这是JWT + Redis混合方案的优势。
     * 
     * @param tokenId 令牌唯一标识（UUID）
     * @param user 用户实体对象
     * @param issueAt 签发时间
     * @param expireAt 过期时间
     * @param tokenVersion 令牌版本号
     * @return 签名后的JWT刷新令牌字符串
     */
    private String createRefreshToken(String tokenId, User user, Instant issueAt, Instant expireAt, long tokenVersion) {
        // 构建刷新令牌的Claims
        // 与访问令牌相比，刷新令牌只包含最基础的信息
        JwtClaimsSet claims = JwtClaimsSet.builder()
                // 标准声明
                .issuer(authProperties.getJwt().getIssuer())    // 发行者
                .issuedAt(issueAt)                              // 签发时间
                .expiresAt(expireAt)                            // 过期时间
                .subject(String.valueOf(user.getId()))          // 主体（用户ID）
                .id(tokenId)                                    // JWT ID
                
                // 自定义声明
                .claim(CLAIM_TOKEN_TYPE, TYPE_REFRESH)          // 令牌类型：标识这是刷新令牌
                .claim(CLAIM_USER_ID, user.getId())             // 用户ID
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)       // 令牌版本号
                .build();
        
        // 使用RSA私钥签名，生成完整的JWT字符串
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * 验证JWT令牌的公共声明（安全检查员）
     * 
     * <p>这个方法在令牌解析后执行，验证令牌的业务声明是否符合预期。
     * jwtDecoder.decode()只验证了JWT的格式、签名和过期时间，
     * 但还需要验证业务层面的规则。
     * 
     * <p>验证项目：
     * <pre>
     * 1. 令牌类型（token_type）验证：
     *    - 确保令牌的用途与预期一致
     *    - 防止用refreshToken访问需要accessToken的接口
     *    - 防止用accessToken调用刷新接口
     *    例如：
     *    - 访问接口时期望TYPE_ACCESS，如果令牌是TYPE_REFRESH，验证失败
     *    - 刷新令牌时期望TYPE_REFRESH，如果令牌是TYPE_ACCESS，验证失败
     * 
     * 2. 发行者（issuer）验证：
     *    - 确保令牌是由我们的系统签发的
     *    - 防止其他系统的令牌混入（如果多个系统共享Redis）
     *    - 防止攻击者使用其他来源的JWT尝试欺骗
     * 
     * 3. 主题（subject）验证：
     *    - 确保用户ID有效
     *    - 调用parseSubjectAsUserId()进行格式和范围验证
     *    - 防止空值、负数等非法用户ID
     * </pre>
     * 
     * <p>为什么需要这些验证？
     * 虽然JWT签名验证了令牌未被篡改，但攻击者可能：
     * - 使用有效的accessToken调用刷新接口
     * - 使用其他系统签发的JWT（如果共享密钥）
     * - 构造特殊格式的JWT尝试绕过验证
     * 
     * 这些业务验证提供了额外的安全层。
     * 
     * @param jwt JWT令牌对象（已通过签名和过期验证）
     * @param expectedType 期望的令牌类型（TYPE_ACCESS或TYPE_REFRESH）
     * @throws BusinessException 如果任何验证失败
     */
    private void validateCommonClaims(Jwt jwt, String expectedType) {
        // 验证1：检查令牌类型
        // 从JWT的payload中提取token_type字段
        // 确保令牌的用途与调用方预期一致
        String tokenType = jwt.getClaimAsString(CLAIM_TOKEN_TYPE);
        if (!expectedType.equals(tokenType)) {
            // 类型不匹配，可能是：
            // - 用refreshToken访问接口（应该用accessToken）
            // - 用accessToken刷新令牌（应该用refreshToken）
            // - 令牌被恶意修改了类型
            throw invalidTokenError(expectedType);
        }

        // 验证2：检查发行者
        // 从JWT中提取issuer字段，与配置的发行者对比
        // 确保令牌是由我们的系统签发的
        String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
        if (!authProperties.getJwt().getIssuer().equals(issuer)) {
            // 发行者不匹配，可能是：
            // - 其他系统签发的JWT
            // - 攻击者伪造的JWT
            throw invalidTokenError(expectedType);
        }

        // 验证3：检查主题（用户ID）
        // 调用parseSubjectAsUserId()验证用户ID的格式和合法性
        // 确保subject不为空、是有效的数字、且大于0
        parseSubjectAsUserId(jwt.getSubject());
    }

    /**
     * 创建无效令牌的业务异常（异常工厂）
     * 
     * <p>这个方法根据令牌类型返回相应的异常对象。
     * 访问令牌和刷新令牌的错误码不同，这样可以：
     * 1. 让调用方知道具体是哪种令牌出了问题
     * 2. 前端可以根据不同错误码显示不同的提示信息
     * 3. 日志中可以更精确地追踪问题
     * 
     * <p>错误码映射：
     * <ul>
     *   <li>TYPE_REFRESH -> INVALID_REFRESH_TOKEN（刷新令牌无效）</li>
     *   <li>TYPE_ACCESS -> UNAUTHORIZED（访问令牌无效/未授权）</li>
     *   <li>其他 -> UNAUTHORIZED（默认）</li>
     * </ul>
     * 
     * @param expectedType 令牌类型
     * @return 对应的BusinessException对象
     */
    private BusinessException invalidTokenError(String expectedType) {
        // 根据令牌类型返回不同的错误码
        // 刷新令牌有专门的错误码，方便前端区分处理
        if (TYPE_REFRESH.equals(expectedType)) {
            return new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        // 访问令牌或其他类型返回通用的未授权错误
        return new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    /**
     * 将刷新令牌设置到HttpOnly Cookie中（公共方法，供Controller调用）
     *
     * <p>安全特性：
     * <ul>
     *   <li><b>HttpOnly</b>: 防止JavaScript访问Cookie，避免XSS攻击窃取令牌</li>
     *   <li><b>Secure</b>: 仅通过HTTPS传输（生产环境必须开启）</li>
     *   <li><b>SameSite=Strict</b>: 防止CSRF攻击，只在同站请求时发送</li>
     *   <li><b>Path=/</b>: Cookie作用域为整个应用</li>
     * </ul>
     *
     * @param response HTTP响应对象
     * @param refreshToken 刷新令牌值
     * @param expiresAt 过期时间
     */
    public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken, Instant expiresAt) {

        // 设置过期时间（从Instant计算最大年龄）
        long maxAgeSeconds = java.time.Duration.between(Instant.now(), expiresAt).getSeconds();
        if (maxAgeSeconds < 0) {
            // 默认7天过期（如果传入的过期时间无效）
            maxAgeSeconds = 60 * 60 * 24 * 7;
            log.warn("刷新令牌过期时间无效，使用默认7天过期");
        }
        
        // 动态判断是否启用Secure标志
        boolean isSecure = shouldUseSecureCookie();

        // 手动构造Set-Cookie响应头（兼容所有Servlet版本，支持SameSite）
        StringBuilder setCookieHeader = new StringBuilder();
        setCookieHeader.append("refreshToken=").append(refreshToken);
        setCookieHeader.append("; Path=/");
        setCookieHeader.append("; Max-Age=").append(maxAgeSeconds);
        setCookieHeader.append("; HttpOnly");
        setCookieHeader.append("; Secure=").append(isSecure);
        setCookieHeader.append("; SameSite=Strict");

        response.addHeader("Set-Cookie", setCookieHeader.toString());

        log.info("✅ 刷新令牌已设置到HttpOnly Cookie - 过期时间: {}秒, Secure: {}, HttpOnly: {}", 
                maxAgeSeconds, isSecure, true);
    }
    
    /**
     * 判断是否应该使用Secure Cookie
     * 
     * <p>优先级：
     * <ol>
     *   <li>系统属性 -Dauth.cookie.secure=true/false</li>
     *   <li>环境变量 AUTH_COOKIE_SECURE=true/false</li>
     *   <li>默认值：false（开发环境）</li>
     * </ol>
     */
    private boolean shouldUseSecureCookie() {
        // 检查系统属性
        String sysProp = System.getProperty("auth.cookie.secure");
        if (sysProp != null) {
            return Boolean.parseBoolean(sysProp.trim());
        }
        
        // 检查环境变量
        String envVar = System.getenv().getOrDefault("AUTH_COOKIE_SECURE", "").trim();
        if (!envVar.isEmpty()) {
            return Boolean.parseBoolean(envVar);
        }
        
        return false;
    }
}
