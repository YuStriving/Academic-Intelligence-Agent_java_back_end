package com.xiaoce.agent.caffine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.beans.BeanProperty;
import java.util.concurrent.TimeUnit;

/**
 * CaffineAcademicConfig
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/26 13:14
 */
@Configuration
@RequiredArgsConstructor
public class CaffineAcademicConfig {

    @Bean("academicIdCache")
    public Cache<String,String> academicIdCache(CaffineProperties properties){
        return Caffeine.newBuilder()
                .maximumSize(properties.getCache().getMaxSize())
                .expireAfterWrite(properties.getCache().getExpireMinutes(), TimeUnit.MINUTES)
                .recordStats()
                .build();
    }
}
