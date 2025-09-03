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
package com.chestnut.system.domain.dto;

import com.chestnut.common.security.domain.BaseDTO;
import com.chestnut.system.fixed.dict.YesOrNo;
import com.chestnut.system.validator.Dict;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;

/**
 * CreateDictDataRequest
 *
 * @author 兮玥
 * @email 190785909@qq.com
 */
@Getter
@Setter
public class CreateDictDataRequest extends BaseDTO {

    @NotBlank
    @Length(max = 100)
    private String dictLabel;

    @NotBlank
    @Length(max = 100)
    private String dictValue;

    @NotBlank
    @Length(max = 100)
    private String dictType;

    @Length(max = 100)
    private String cssClass;

    @Length(max = 100)
    private String listClass;

    private Long dictSort;

    @Dict(YesOrNo.TYPE)
    private String isDefault;

    @Length(max = 500)
    private String remark;
}
