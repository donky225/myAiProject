package com.ai.llm.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final StringRedisTemplate redisTemplate;

    public CacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String get(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                log.info("[Cache] HIT: {}", key);
            }
            return value;
        } catch (Exception e) {
            log.warn("[Cache] Redis 조회 실패, 캐시 없이 진행: {}", e.getMessage());
            return null; // Redis가 죽어있어도 앱은 정상 동작해야 함 (캐시는 선택사항)
        }
    }

    public void put(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            log.info("[Cache] SET: {} (TTL={}분)", key, ttl.toMinutes());
        } catch (Exception e) {
            log.warn("[Cache] Redis 저장 실패, 무시하고 진행: {}", e.getMessage());
        }
    }
}