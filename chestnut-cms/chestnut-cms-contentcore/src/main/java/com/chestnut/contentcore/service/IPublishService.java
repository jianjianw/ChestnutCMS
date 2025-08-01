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
package com.chestnut.contentcore.service;

import com.chestnut.common.async.AsyncTask;
import com.chestnut.common.security.domain.LoginUser;
import com.chestnut.contentcore.core.IContent;
import com.chestnut.contentcore.core.IInternalDataType;
import com.chestnut.contentcore.core.IPageWidget;
import com.chestnut.contentcore.domain.CmsCatalog;
import com.chestnut.contentcore.domain.CmsContent;
import com.chestnut.contentcore.domain.CmsPageWidget;
import com.chestnut.contentcore.domain.CmsSite;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.util.List;

public interface IPublishService {

    /**
     * 发布站点首页<br>
     * 此方法供API发布站点首页调用，做基础校验
     *
     * @param site 站点
     * @throws IOException e1
     * @throws TemplateException e2
     */
    void publishSiteIndex(CmsSite site) throws IOException, TemplateException;

    /**
     * 发布全站，异步任务
     * <p>
     * 发布站点下所有栏目及指定状态内容
     *
     * @param site 站点
     * @param contentStatus 内容状态
     * @return 结果
     */
    AsyncTask publishAll(CmsSite site, final String contentStatus, final LoginUser operator);

    /**
     * 站点首页页面内容
     *
     * @param site 站点
     * @param requestData 请求数据
     * @return 结果
     * @throws IOException e1
     * @throws TemplateException e2
     */
    String getSitePageData(CmsSite site, IInternalDataType.RequestData requestData)
            throws IOException, TemplateException;

    /**
     * 获取栏目模板页面内容
     *
     * @param catalog 栏目
     * @param requestData 请求数据
     * @param listFlag 是否分页
     * @return 结果
     * @throws IOException e1
     * @throws TemplateException e2
     */
    String getCatalogPageData(CmsCatalog catalog, IInternalDataType.RequestData requestData, boolean listFlag)
            throws IOException, TemplateException;

    /**
     * 发布栏目，异步任务
     *
     * @param catalog 栏目
     * @param publishChild  是否发布子栏目
     * @param publishDetail 是否发布详情页
     * @param publishStatus 指定发布内容状态
     * @return 结果
     */
    AsyncTask publishCatalog(CmsCatalog catalog, boolean publishChild, boolean publishDetail,
                             String publishStatus, final LoginUser operator);

    /**
     * 获取内容模板页面结果
     *
     * @param content 内容
     * @param requestData 请求数据
     * @return 结果
     * @throws IOException e1
     * @throws TemplateException e2
     */
    String getContentPageData(CmsContent content, IInternalDataType.RequestData requestData)
            throws IOException, TemplateException;

    /**
     * 内容静态化
     *
     * @param content 内容
     */
    void asyncStaticizeContent(IContent<?> content);

    /**
     * 发布内容
     *
     * @param contents 内容列表
     * @param operator 操作人
     */
    AsyncTask publishContents(List<CmsContent> contents, LoginUser operator);

    void publishContent(CmsContent content, LoginUser operator);

    /**
     * 获取内容扩展模板解析内容
     *
     * @param content 内容
     * @param publishPipeCode 发布通道编码
     * @param isPreview 是否预览
     * @return 结果
     * @throws IOException e1
     * @throws TemplateException e2
     */
    String getContentExPageData(CmsContent content, String publishPipeCode, boolean isPreview)
            throws IOException, TemplateException;

    /**
     * 获取页面部件模板解析内容
     *
     * @param pageWidget 页面部件
     * @param publishPipeCode 发布通道编码
     * @param isPreview 是否预览
     * @return 结果
     * @throws IOException e1
     * @throws TemplateException e2
     */
    String getPageWidgetPageData(CmsPageWidget pageWidget, String publishPipeCode, boolean isPreview) throws IOException, TemplateException;

    /**
     * 页面部件静态化
     *
     * @param pageWidget 页面部件
     */
    void pageWidgetStaticize(IPageWidget pageWidget);
}
