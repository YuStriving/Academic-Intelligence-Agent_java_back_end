package com.xiaoce.agent.auth.common.constant;

public final class RefreshToken {

    private RefreshToken() {
    }

    // refresh token index per user
    public static final String USER_SET_KEY_PREFIX = "auth:refresh:user:";

    // marker key for each jti expiration
    public static final String TOKEN_KEY_PREFIX = "auth:refresh:jti:expire:";

    // main hash table for all refresh tokens
    public static final String MAIN_HASH_KEY_PREFIX = "auth:refresh:tokens:hash";

    // optional block key (legacy compatibility)
    public static final String USER_BLOCK_KEY_PREFIX = "auth:user:block:";

    public static final String BAN_USER_PERMANENT_KEY_PREFIX = "auth:ban:user:";

    // token version key by userId, used for immediate invalidation after kick-offline
    public static final String USER_TOKEN_VERSION_KEY_PREFIX = "auth:user:token:version:";
}
