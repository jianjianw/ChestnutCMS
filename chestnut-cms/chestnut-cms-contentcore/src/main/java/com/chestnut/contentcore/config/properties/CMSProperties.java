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
package com.chestnut.contentcore.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CMS配置属性
 *
 * @author 兮玥
 * @email 190785909@qq.com
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "chestnut.cms")
public class CMSProperties {
	
	/**
	 *	资源文件根目录 
	 */
	private String resourceRoot;
	
	/**
	 * 缓存名统一前缀
	 */
	private String cacheName = "cms:";
	
	/**
	 * 系统启动时是否清空cacheName前缀的所有缓存
	 */
	private Boolean resetCache = false;

	/**
	 * 资源分片文件过期时间，默认：24小时，单位：秒
	 */
	private long resourceChunkExpireSeconds = 24 * 60 * 60;
}
