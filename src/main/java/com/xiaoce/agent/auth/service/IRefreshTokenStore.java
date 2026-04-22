package com.xiaoce.agent.auth.service;

import java.time.Duration;

/**
 * RefreshTokenStore
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/22 9:52
 */
public interface IRefreshTokenStore {
    void saveRefreshToken(Long userId, String tokenId, Duration ttl);

    boolean validateRefreshTokenOwnership(Long userId, String tokenId);

    void removeRefreshToken(Long userId, String tokenId);

    void removeUserAllRefreshToken(Long userId);
}
