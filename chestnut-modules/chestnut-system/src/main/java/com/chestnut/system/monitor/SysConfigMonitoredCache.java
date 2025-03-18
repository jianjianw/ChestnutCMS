/*
 * Copyright 2022-2025 兮玥(190785909@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.chestnut.system.monitor;

import com.chestnut.common.redis.IMonitoredCache;
import com.chestnut.common.redis.RedisCache;
import com.chestnut.system.SysConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * SysConfigMonitoredCache
 *
 * @author 兮玥
 * @email 190785909@qq.com
 */
@Component(IMonitoredCache.BEAN_PREFIX + SysConfigMonitoredCache.ID)
@RequiredArgsConstructor
public class SysConfigMonitoredCache implements IMonitoredCache<String> {

    public static final String ID = "SysConfig";

    private final RedisCache redisCache;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getCacheName() {
        return "{MONITORED.CACHE.CONFIG}";
    }

    @Override
    public String getCache(String cacheKey) {
        return redisCache.getCacheObject(cacheKey, String.class);
    }

    @Override
    public String getCacheKey() {
        return SysConstants.CACHE_SYS_CONFIG_KEY;
    }
}
