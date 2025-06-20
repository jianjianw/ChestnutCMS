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
package com.chestnut.common.captcha.text;

import com.chestnut.common.captcha.*;
import com.chestnut.common.captcha.config.properties.CaptchaProperties;
import com.chestnut.common.utils.IdUtils;
import com.chestnut.common.utils.StringUtils;
import com.chestnut.common.utils.image.ImageUtils;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * TextCaptchaType
 *
 * @author 兮玥
 * @email 190785909@qq.com
 */
@Slf4j
@RequiredArgsConstructor
@Component(ICaptchaType.BEAN_PREFIX + TextCaptchaType.TYPE)
public class TextCaptchaType implements ICaptchaType {

    public final static String TYPE = "Text";
    private final static String IMAGE_TYPE_GIF = "gif";

    private final static String CACHE_KEY_GET = "cc:captcha:text:"; // 待验证缓存

    private final TextKaptchaProducer textKaptchaProducer;

    private final ICaptchaStorage captchaStorage;

    protected final Validator validator;

    private final CaptchaProperties captchaProperties;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Object create(CaptchaData captchaData) {
        try {
            String capStr = textKaptchaProducer.createText();
            BufferedImage image = textKaptchaProducer.createImage(capStr);
            String imageBase64Str = ImageUtils.imageToBase64(image, IMAGE_TYPE_GIF);
            String token = captchaData.getToken();
            if (StringUtils.isEmpty(token)) {
                token = IdUtils.simpleUUID();
            }
            // 写入缓存
            captchaStorage.set(CACHE_KEY_GET + token, capStr, captchaProperties.getExpireSeconds());
            // 返回客户端
            return new TextCaptchaVO(token, imageBase64Str);
        } catch (IOException e) {
            throw CaptchaErrorCode.GENERATE_CAPTCHA_FAILED.exception(TYPE);
        }
    }

    @Override
    public CaptchaCheckResult check(CaptchaData captchaData) {
        try {
            if (!validate(captchaData.getToken(), captchaData.getData())) {
                return CaptchaCheckResult.fail();
            }
            return CaptchaCheckResult.success();
        } finally {
            this.captchaStorage.delete(CACHE_KEY_GET + captchaData.getToken());
        }
    }

    private boolean validate(String token, String code) {
        String cacheKey = CACHE_KEY_GET + token;
        if (!this.captchaStorage.has(cacheKey)) {
            return false;
        }
        String cacheCode = this.captchaStorage.get(cacheKey);
        return cacheCode.equals(code);
    }

    @Override
    public boolean isTokenValidated(CaptchaData captchaData) {
        return validate(captchaData.getToken(), captchaData.getData());
    }
}
