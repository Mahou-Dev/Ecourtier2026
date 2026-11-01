package com.ecourtier.common.config;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("adminStats") {
            @Override
            protected Cache createConcurrentMapCache(String name) {
                return new ExpiringConcurrentMapCache(name, 60000); // 60s TTL
            }
        };
    }

    private static class ExpiringConcurrentMapCache extends ConcurrentMapCache {
        private final long ttlMillis;
        private final ConcurrentHashMap<Object, Long> entryTimes = new ConcurrentHashMap<>();

        public ExpiringConcurrentMapCache(String name, long ttlMillis) {
            super(name);
            this.ttlMillis = ttlMillis;
        }

        @Override
        public ValueWrapper get(Object key) {
            Long time = entryTimes.get(key);
            if (time != null && System.currentTimeMillis() - time > ttlMillis) {
                evict(key);
                return null;
            }
            return super.get(key);
        }

        @Override
        public void put(Object key, Object value) {
            entryTimes.put(key, System.currentTimeMillis());
            super.put(key, value);
        }

        @Override
        public void evict(Object key) {
            entryTimes.remove(key);
            super.evict(key);
        }

        @Override
        public void clear() {
            entryTimes.clear();
            super.clear();
        }
    }
}
