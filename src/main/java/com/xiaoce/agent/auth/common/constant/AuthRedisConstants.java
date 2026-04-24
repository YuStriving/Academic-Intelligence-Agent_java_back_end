package com.xiaoce.agent.auth.common.constant;

public final class AuthRedisConstants {

    private AuthRedisConstants() {
    }

    // refresh token index per user
    public static final String USER_SET_KEY_PREFIX = "auth:refresh:user:";

    // marker key for each jti expiration
    public static final String TOKEN_KEY_PREFIX = "auth:refresh:jti:expire:";

    // token version key by userId, used for immediate invalidation after kick-offline
    public static final String USER_TOKEN_VERSION_KEY_PREFIX = "auth:user:token:version:";

    // global expiry zset key, score=expireAtEpochSeconds, member=userId|jti
    public static final String GLOBAL_EXPIRY_ZSET_KEY = "auth:refresh:tokens:expiry";

    // bitmap key for banned users: offset=userId, bit=1 means banned
    public static final String USER_IS_BANNED_BITMAP_KEY = "auth:user:is:banned:bitmap";
}
