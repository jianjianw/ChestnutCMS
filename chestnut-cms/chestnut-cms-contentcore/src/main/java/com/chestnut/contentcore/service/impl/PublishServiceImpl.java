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
package com.chestnut.contentcore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chestnut.common.async.AsyncTask;
import com.chestnut.common.async.AsyncTaskManager;
import com.chestnut.common.security.domain.LoginUser;
import com.chestnut.common.staticize.StaticizeService;
import com.chestnut.common.staticize.core.TemplateContext;
import com.chestnut.common.utils.Assert;
import com.chestnut.common.utils.StringUtils;
import com.chestnut.common.utils.file.FileExUtils;
import com.chestnut.contentcore.core.*;
import com.chestnut.contentcore.core.impl.CatalogType_Link;
import com.chestnut.contentcore.core.impl.PublishPipeProp_ContentTemplate;
import com.chestnut.contentcore.core.impl.PublishPipeProp_DefaultListTemplate;
import com.chestnut.contentcore.domain.*;
import com.chestnut.contentcore.enums.ContentCopyType;
import com.chestnut.contentcore.exception.ContentCoreErrorCode;
import com.chestnut.contentcore.listener.event.AfterContentPublishEvent;
import com.chestnut.contentcore.publish.IPublishStrategy;
import com.chestnut.contentcore.publish.staticize.CatalogStaticizeType;
import com.chestnut.contentcore.publish.staticize.ContentStaticizeType;
import com.chestnut.contentcore.publish.staticize.SiteStaticizeType;
import com.chestnut.contentcore.service.*;
import com.chestnut.contentcore.template.ITemplateType;
import com.chestnut.contentcore.template.impl.CatalogTemplateType;
import com.chestnut.contentcore.template.impl.ContentTemplateType;
import com.chestnut.contentcore.template.impl.SiteTemplateType;
import com.chestnut.contentcore.util.*;
import com.chestnut.system.fixed.dict.YesOrNo;
import freemarker.template.TemplateException;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PublishServiceImpl implements IPublishService, ApplicationContextAware {

	private static final Logger logger = LoggerFactory.getLogger(PublishServiceImpl.class);

	private final ISiteService siteService;

	private final ICatalogService catalogService;

	private final IContentService contentService;

	private final ITemplateService templateService;

	private final IPublishPipeService publishPipeService;

	private final StaticizeService staticizeService;

	private final AsyncTaskManager asyncTaskManager;

	private final IPublishStrategy publishStrategy;

	private ApplicationContext applicationContext;

	@Override
	public String getSitePageData(CmsSite site, IInternalDataType.RequestData requestData)
			throws IOException, TemplateException {
		String indexTemplate = site.getIndexTemplate(requestData.getPublishPipeCode());
		File templateFile = this.templateService.findTemplateFile(site, indexTemplate, requestData.getPublishPipeCode());
		if (Objects.isNull(templateFile)) {
			throw ContentCoreErrorCode.TEMPLATE_EMPTY.exception(requestData.getPublishPipeCode(), indexTemplate);
		}
		// 模板ID = 通道:站点目录:模板文件名
		String templateKey = SiteUtils.getTemplateKey(site, requestData.getPublishPipeCode(), indexTemplate);
		TemplateContext context = new TemplateContext(templateKey, requestData.isPreview(), requestData.getPublishPipeCode());
		// init template datamode
		TemplateUtils.initGlobalVariables(site, context);
		context.getVariables().put(TemplateUtils.TemplateVariable_Request, Objects.requireNonNullElse(requestData.getParams(), Map.of()));
		// init templateType data to datamode
		ITemplateType templateType = templateService.getTemplateType(SiteTemplateType.TypeId);
		templateType.initTemplateData(site.getSiteId(), context);

		long s = System.currentTimeMillis();
		try (StringWriter writer = new StringWriter()) {
			this.staticizeService.process(context, writer);
			return writer.toString();
		} finally {
			logger.debug("[{}]首页模板解析：{}\t耗时：{}ms", requestData.getPublishPipeCode(), site.getName(), System.currentTimeMillis() - s);
		}
	}

	@Override
	public void publishSiteIndex(CmsSite site) {
		// 发布所有通道页面
		List<CmsPublishPipe> publishPipes = this.publishPipeService.getPublishPipes(site.getSiteId());
		Assert.isTrue(!publishPipes.isEmpty(), ContentCoreErrorCode.NO_PUBLISHPIPE::exception);

		asyncPublishSite(site);
	}

	@Override
	public AsyncTask publishAll(CmsSite site, final String contentStatus, final LoginUser operator) {
		AsyncTask asyncTask = new AsyncTask() {

			@Override
			public void run0() throws InterruptedException {
				// 发布全站先清理所有模板缓存
				templateService.clearSiteAllTemplateStaticContentCache(site);

				List<CmsCatalog> catalogList = catalogService
						.list(new LambdaQueryWrapper<CmsCatalog>().eq(CmsCatalog::getSiteId, site.getSiteId()));
				for (CmsCatalog catalog : catalogList) {
					// 先发布内容
					int pageSize = 500;
					long lastContentId = 0L;
					long total = contentService.dao().lambdaQuery().eq(CmsContent::getCatalogId, catalog.getCatalogId())
							.eq(CmsContent::getStatus, contentStatus)
							.ne(CmsContent::getLinkFlag, YesOrNo.YES)
							.count();
					int count = 1;
					while (true) {
						LambdaQueryWrapper<CmsContent> q = new LambdaQueryWrapper<CmsContent>()
								.eq(CmsContent::getCatalogId, catalog.getCatalogId())
								.eq(CmsContent::getStatus, contentStatus)
								.ne(CmsContent::getLinkFlag, YesOrNo.YES)
								.gt(CmsContent::getContentId, lastContentId)
								.orderByAsc(CmsContent::getContentId);
						Page<CmsContent> page = contentService.dao().page(new Page<>(0, pageSize, false), q);
						for (CmsContent xContent : page.getRecords()) {
							this.setProgressInfo((int) (count * 100 / total),
									"正在发布内容：" + catalog.getName() + "[" + count + " / " + total + "]");
							lastContentId = xContent.getContentId();
							IContentType contentType = ContentCoreUtils.getContentType(xContent.getContentType());
							IContent<?> content = contentType.newContent();
							content.setContentEntity(xContent);
							content.setOperator(operator);
							content.publish();
							this.checkInterrupt();
							count++;
						}
						if (page.getRecords().size() < pageSize) {
							break;
						}
					}
				}
				// 发布栏目
				for (int i = 0; i < catalogList.size(); i++) {
					CmsCatalog catalog = catalogList.get(i);
					this.setProgressInfo((i * 100) / catalogList.size(), "正在发布栏目：" + catalog.getName());
					asyncPublishCatalog(catalog);
					this.checkInterrupt(); // 允许中断
				}
				// 发布站点
				this.setProgressInfo(99, "正在发布首页：" + site.getName());
				asyncPublishSite(site);
				this.setProgressInfo(100, "发布完成");
			}
		};
		asyncTask.setType("Publish");
		asyncTask.setTaskId("Publish-Site-" + site.getSiteId());
		asyncTask.setInterruptible(true);
		this.asyncTaskManager.execute(asyncTask);
		return asyncTask;
	}

	private void asyncPublishSite(CmsSite site) {
		publishStrategy.publish(SiteStaticizeType.TYPE, site.getSiteId().toString());
	}

	@Override
	public String getCatalogPageData(CmsCatalog catalog, IInternalDataType.RequestData requestData, boolean listFlag)
			throws IOException, TemplateException {
		if (CatalogType_Link.ID.equals(catalog.getCatalogType())) {
			throw new RuntimeException("链接类型栏目无独立页面：" + catalog.getName());
		}
		String templateFilename = catalog.getListTemplate(requestData.getPublishPipeCode());
		if (!listFlag && requestData.getPageIndex() == 1) {
			// 获取首页模板
			String indexTemplate = catalog.getIndexTemplate(requestData.getPublishPipeCode());
			if (StringUtils.isNotEmpty(indexTemplate)) {
				templateFilename = indexTemplate;
			} else {
				listFlag = true;
			}
		}
		CmsSite site = this.siteService.getById(catalog.getSiteId());
		if (StringUtils.isEmpty(templateFilename)) {
			// 站点默认模板
			templateFilename = PublishPipeProp_DefaultListTemplate.getValue(requestData.getPublishPipeCode(),
					site.getPublishPipeProps());
		}
		final String template = templateFilename;
		File templateFile = this.templateService.findTemplateFile(site, template, requestData.getPublishPipeCode());
		Assert.notNull(templateFile, () -> ContentCoreErrorCode.TEMPLATE_EMPTY.exception(requestData.getPublishPipeCode(), template));

		long s = System.currentTimeMillis();
		// 生成静态页面
		String templateKey = SiteUtils.getTemplateKey(site, requestData.getPublishPipeCode(), template);
		TemplateContext templateContext = new TemplateContext(templateKey, requestData.isPreview(), requestData.getPublishPipeCode());
		templateContext.setPageIndex(requestData.getPageIndex());
		// init template variables
		TemplateUtils.initGlobalVariables(site, templateContext);
		templateContext.getVariables().put(TemplateUtils.TemplateVariable_Request, Objects.requireNonNullElse(requestData.getParams(), Map.of()));
		// init templateType variables
		ITemplateType templateType = templateService.getTemplateType(CatalogTemplateType.TypeId);
		templateType.initTemplateData(catalog.getCatalogId(), templateContext);
		// 分页链接
		if (listFlag) {
			String catalogLink = this.catalogService.getCatalogListLink(catalog, 1, requestData.getPublishPipeCode(), requestData.isPreview());
			templateContext.setFirstFileName(catalogLink);
			templateContext.setOtherFileName(catalogLink + "&pi=" + TemplateContext.PlaceHolder_PageNo);
		}
		try (StringWriter writer = new StringWriter()) {
			this.staticizeService.process(templateContext, writer);
			return writer.toString();
		} finally {
			logger.debug("[{}]栏目页模板解析：{}，耗时：{}ms", requestData.getPublishPipeCode(), catalog.getName(),
					(System.currentTimeMillis() - s));
		}
	}

	@Override
	public AsyncTask publishCatalog(CmsCatalog catalog, boolean publishChild, boolean publishDetail,
			final String publishStatus, final LoginUser operator) {
		List<CmsPublishPipe> publishPipes = publishPipeService.getPublishPipes(catalog.getSiteId());
		Assert.isTrue(!publishPipes.isEmpty(), ContentCoreErrorCode.NO_PUBLISHPIPE::exception);

		AsyncTask asyncTask = new AsyncTask() {

			@Override
			public void run0() throws InterruptedException {
				List<CmsCatalog> catalogs = new ArrayList<>();
				catalogs.add(catalog);
				// 是否包含子栏目
				if (publishChild) {
					LambdaQueryWrapper<CmsCatalog> q = new LambdaQueryWrapper<CmsCatalog>()
							.eq(CmsCatalog::getStaticFlag, YesOrNo.YES) // 可静态化
							.eq(CmsCatalog::getVisibleFlag, YesOrNo.YES) // 可见
							.likeRight(CmsCatalog::getAncestors, catalog.getAncestors());
					catalogs.addAll(catalogService.list(q));
				}
				// 先发布内容
				if (publishDetail) {
					for (CmsCatalog catalog : catalogs) {
						int pageSize = 100;
						long lastContentId = 0L;
						long total = contentService.dao().lambdaQuery().eq(CmsContent::getCatalogId, catalog.getCatalogId())
								.eq(CmsContent::getStatus, publishStatus)
								.ne(CmsContent::getLinkFlag, YesOrNo.YES)
								.count();
						int count = 1;
						while (true) {
							LambdaQueryWrapper<CmsContent> q = new LambdaQueryWrapper<CmsContent>()
									.eq(CmsContent::getCatalogId, catalog.getCatalogId())
									.eq(CmsContent::getStatus, publishStatus)
									.ne(CmsContent::getLinkFlag, YesOrNo.YES)
									.gt(CmsContent::getContentId, lastContentId)
									.orderByAsc(CmsContent::getContentId);
							Page<CmsContent> page = contentService.dao().page(new Page<>(0, pageSize, false), q);
							for (CmsContent xContent : page.getRecords()) {
								this.setProgressInfo((int) (count * 100 / total),
										"正在发布内容：" + catalog.getName() + "[" + count + " / " + total + "]");
								lastContentId = xContent.getContentId();
								IContentType contentType = ContentCoreUtils.getContentType(xContent.getContentType());
								IContent<?> content = contentType.newContent();
								content.setContentEntity(xContent);
								content.setOperator(operator);
								content.publish();
								this.checkInterrupt();
								count++;
							}
							if (page.getRecords().size() < pageSize) {
								break;
							}
						}
					}
				}
				// 发布栏目
				for (int i = 0; i < catalogs.size(); i++) {
					CmsCatalog catalog = catalogs.get(i);
					this.setProgressInfo((i * 100) / catalogs.size(), "正在发布栏目：" + catalog.getName());
					asyncPublishCatalog(catalog);
					this.checkInterrupt(); // 允许中断
				}
				// 发布站点
				this.setPercent(99);
				asyncPublishSite(siteService.getSite(catalog.getSiteId()));
				this.setProgressInfo(100, "发布完成");
			}
		};
		asyncTask.setType("Publish");
		asyncTask.setTaskId("Publish-Catalog-" + catalog.getCatalogId());
		asyncTask.setInterruptible(true);
		this.asyncTaskManager.execute(asyncTask);
		return asyncTask;
	}

	public void asyncPublishCatalog(final CmsCatalog catalog) {
		if (CatalogType_Link.ID.equals(catalog.getCatalogType())) {
			return; // 链接栏目直接跳过
		}
		publishStrategy.publish(CatalogStaticizeType.TYPE, catalog.getCatalogId().toString());
	}

	private String getDetailTemplate(CmsSite site, CmsCatalog catalog, CmsContent content, String publishPipeCode) {
		String detailTemplate = PublishPipeProp_ContentTemplate.getValue(publishPipeCode,
				content.getPublishPipeProps());
		if (StringUtils.isEmpty(detailTemplate)) {
			// 无内容独立模板取栏目配置
			detailTemplate = this.publishPipeService.getPublishPipePropValue(
					IPublishPipeProp.DetailTemplatePropPrefix + content.getContentType(), publishPipeCode,
					catalog.getPublishPipeProps());
			if (StringUtils.isEmpty(detailTemplate)) {
				// 无栏目配置去站点默认模板配置
				detailTemplate = this.publishPipeService.getPublishPipePropValue(
						IPublishPipeProp.DefaultDetailTemplatePropPrefix + content.getContentType(), publishPipeCode,
						site.getPublishPipeProps());
			}
		}
		return detailTemplate;
	}

	@Override
	public String getContentPageData(CmsContent content, IInternalDataType.RequestData requestData)
			throws IOException, TemplateException {
		CmsSite site = this.siteService.getById(content.getSiteId());
		CmsCatalog catalog = this.catalogService.getCatalog(content.getCatalogId());
		if (content.isLinkContent()) {
			throw new RuntimeException("标题内容：" + content.getTitle() + "，跳转链接：" + content.getRedirectUrl());
		}
		// 查找模板
		final String detailTemplate = getDetailTemplate(site, catalog, content, requestData.getPublishPipeCode());
		File templateFile = this.templateService.findTemplateFile(site, detailTemplate, requestData.getPublishPipeCode());
		Assert.notNull(templateFile,
				() -> ContentCoreErrorCode.TEMPLATE_EMPTY.exception(requestData.getPublishPipeCode(), detailTemplate));

		long s = System.currentTimeMillis();
		// 生成静态页面
		try (StringWriter writer = new StringWriter()) {
			IContentType contentType = ContentCoreUtils.getContentType(content.getContentType());
			// 模板ID = 通道:站点目录:模板文件名
			String templateKey = SiteUtils.getTemplateKey(site, requestData.getPublishPipeCode(), detailTemplate);
			TemplateContext templateContext = new TemplateContext(templateKey, requestData.isPreview(), requestData.getPublishPipeCode());
			templateContext.setPageIndex(requestData.getPageIndex());
			templateContext.getVariables().put(TemplateUtils.TemplateVariable_Request, Objects.requireNonNullElse(requestData.getParams(), Map.of()));
			// init template datamode
			TemplateUtils.initGlobalVariables(site, templateContext);
			// init templateType data to datamode
			ITemplateType templateType = this.templateService.getTemplateType(ContentTemplateType.TypeId);
			templateType.initTemplateData(content.getContentId(), templateContext);
			// 分页链接
			String contentLink = this.contentService.getContentLink(content, 1, requestData.getPublishPipeCode(), requestData.isPreview());
			templateContext.setFirstFileName(contentLink);
			templateContext.setOtherFileName(contentLink + "&pi=" + TemplateContext.PlaceHolder_PageNo);
			// staticize
			this.staticizeService.process(templateContext, writer);
			logger.debug("[{}][{}]内容模板解析：{}，耗时：{}", requestData.getPublishPipeCode(), contentType.getId(), content.getTitle(),
					System.currentTimeMillis() - s);
			return writer.toString();
		}
	}

	/**
	 * 内容发布
	 */
	@Override
	public void publishContent(List<Long> contentIds, LoginUser operator) {
		List<CmsContent> list = this.contentService.dao().listByIds(contentIds);
		if (list.isEmpty()) {
			return;
		}
		asyncTaskManager.execute(() -> {
			// 发布内容
			Set<Long> catalogIds = new HashSet<>();
			for (CmsContent cmsContent : list) {
				IContentType contentType = ContentCoreUtils.getContentType(cmsContent.getContentType());
				IContent<?> content = contentType.loadContent(cmsContent);
				content.setOperator(operator);

				catalogIds.add(cmsContent.getCatalogId());
				if (content.publish()) {
					applicationContext.publishEvent(new AfterContentPublishEvent(contentType, content));
				}
			}
			// 发布关联栏目：内容所属栏目及其所有父级栏目
			Map<Long, CmsCatalog> catalogMap = new HashMap<>();
			catalogIds.forEach(catalogId -> {
				CmsCatalog catalog = catalogService.getCatalog(catalogId);
				catalogMap.put(catalog.getCatalogId(), catalog);
				long parentId = catalog.getParentId();
				while (parentId > 0) {
					CmsCatalog parent = catalogService.getCatalog(parentId);
					if (parent == null) {
						break;
					}
					catalogMap.put(parent.getCatalogId(), parent);
					parentId = parent.getParentId();
				}
			});
			CmsSite site = siteService.getSite(list.get(0).getSiteId());
			catalogMap.values().forEach(this::asyncPublishCatalog);
			// 发布站点首页
			asyncPublishSite(site);
		});
	}

	@Override
	public void asyncPublishContent(IContent<?> content) {
		CmsCatalog catalog = this.catalogService.getCatalog(content.getCatalogId());
		if (!catalog.isStaticize()) {
			return;
		}
		List<CmsPublishPipe> publishPipeCodes = this.publishPipeService.getPublishPipes(content.getSiteId());
		if (publishPipeCodes.isEmpty()) {
			return;
		}
		publishStrategy.publish(ContentStaticizeType.TYPE, content.getContentEntity().getContentId().toString());
		// 关联内容静态化，映射的引用内容
		LambdaQueryWrapper<CmsContent> q = new LambdaQueryWrapper<CmsContent>()
				.eq(CmsContent::getCopyId, content.getContentEntity().getContentId())
				.eq(CmsContent::getCopyType, ContentCopyType.Mapping);
		List<CmsContent> mappingContents = contentService.dao().list(q);
		for (CmsContent mappingContent : mappingContents) {
			publishStrategy.publish(ContentStaticizeType.TYPE, mappingContent.getContentId().toString());
		}
	}

	@Override
	public String getContentExPageData(CmsContent content, String publishPipeCode, boolean isPreview)
			throws IOException, TemplateException {
		CmsSite site = this.siteService.getById(content.getSiteId());
		CmsCatalog catalog = this.catalogService.getCatalog(content.getCatalogId());
		if (!catalog.isStaticize() ) {
			throw new RuntimeException("栏目设置不静态化：" + content.getTitle());
		}
		if (content.isLinkContent()) {
			throw new RuntimeException("标题内容：" + content.getTitle() + "，跳转链接：" + content.getRedirectUrl());
		}
		String exTemplate = ContentUtils.getContentExTemplate(content, catalog, publishPipeCode);
		// 查找模板
		File templateFile = this.templateService.findTemplateFile(site, exTemplate, publishPipeCode);
		Assert.notNull(templateFile,
				() -> ContentCoreErrorCode.TEMPLATE_EMPTY.exception(publishPipeCode, exTemplate));

		long s = System.currentTimeMillis();
		// 生成静态页面
		try (StringWriter writer = new StringWriter()) {
			IContentType contentType = ContentCoreUtils.getContentType(content.getContentType());
			// 模板ID = 通道:站点目录:模板文件名
			String templateKey = SiteUtils.getTemplateKey(site, publishPipeCode, exTemplate);
			TemplateContext templateContext = new TemplateContext(templateKey, isPreview, publishPipeCode);
			// init template data mode
			TemplateUtils.initGlobalVariables(site, templateContext);
			// init templateType data to data mode
			ITemplateType templateType = this.templateService.getTemplateType(ContentTemplateType.TypeId);
			templateType.initTemplateData(content.getContentId(), templateContext);
			// staticize
			this.staticizeService.process(templateContext, writer);
			logger.debug("[{}][{}]内容扩展模板解析：{}，耗时：{}", publishPipeCode, contentType.getId(), content.getTitle(),
					System.currentTimeMillis() - s);
			return writer.toString();
		}
	}

	@Override
	public String getPageWidgetPageData(CmsPageWidget pageWidget, boolean isPreview)
			throws IOException, TemplateException {
		CmsSite site = this.siteService.getById(pageWidget.getSiteId());
		File templateFile = this.templateService.findTemplateFile(site, pageWidget.getTemplate(),
				pageWidget.getPublishPipeCode());
		Assert.notNull(templateFile,
				() -> ContentCoreErrorCode.TEMPLATE_EMPTY.exception(pageWidget.getName(), pageWidget.getCode()));

		// 生成静态页面
		try (StringWriter writer = new StringWriter()) {
			long s = System.currentTimeMillis();
			// 模板ID = 通道:站点目录:模板文件名
			String templateKey = SiteUtils.getTemplateKey(site, pageWidget.getPublishPipeCode(),
					pageWidget.getTemplate());
			TemplateContext templateContext = new TemplateContext(templateKey, isPreview,
					pageWidget.getPublishPipeCode());
			// init template global variables
			TemplateUtils.initGlobalVariables(site, templateContext);
			templateContext.getVariables().put(TemplateUtils.TemplateVariable_PageWidget, pageWidget);
			// init templateType data to datamode
			ITemplateType templateType = this.templateService.getTemplateType(SiteTemplateType.TypeId);
			templateType.initTemplateData(site.getSiteId(), templateContext);
			// staticize
			this.staticizeService.process(templateContext, writer);
			logger.debug("[{}]页面部件【{}#{}】模板解析耗时：{}ms", pageWidget.getPublishPipeCode(), pageWidget.getName(),
					pageWidget.getCode(), System.currentTimeMillis() - s);
			return writer.toString();
		}
	}

	@Override
	public void pageWidgetStaticize(IPageWidget pageWidget) {
		long s = System.currentTimeMillis();
		CmsPageWidget pw = pageWidget.getPageWidgetEntity();
		CmsSite site = this.siteService.getSite(pw.getSiteId());
		File templateFile = this.templateService.findTemplateFile(site, pw.getTemplate(), pw.getPublishPipeCode());
		if (Objects.isNull(templateFile)) {
			logger.warn(StringUtils.messageFormat("页面部件【{0}%s#{1}%s】模板未配置或文件不存在", pw.getName(), pw.getCode()));
			return;
		}
		try {
			// 静态化目录
			String dirPath = SiteUtils.getSiteRoot(site, pw.getPublishPipeCode()) + pw.getPath();
			FileExUtils.mkdirs(dirPath);
			// 自定义模板上下文
			String templateKey = SiteUtils.getTemplateKey(site, pw.getPublishPipeCode(), pw.getTemplate());
			TemplateContext templateContext = new TemplateContext(templateKey, false, pw.getPublishPipeCode());
			templateContext.setDirectory(dirPath);
			String staticFileName = PageWidgetUtils.getStaticFileName(pw, site.getStaticSuffix(pw.getPublishPipeCode()));
			templateContext.setFirstFileName(staticFileName);
			// init template datamode
			TemplateUtils.initGlobalVariables(site, templateContext);
			templateContext.getVariables().put(TemplateUtils.TemplateVariable_PageWidget, pw);
			// init templateType data to datamode
			ITemplateType templateType = templateService.getTemplateType(SiteTemplateType.TypeId);
			templateType.initTemplateData(site.getSiteId(), templateContext);
			// staticize
			this.staticizeService.process(templateContext);
			logger.debug("[{}]页面部件模板解析：{}，耗时：{}ms", pw.getPublishPipeCode(), pw.getCode(), System.currentTimeMillis() - s);
		} catch (TemplateException | IOException e) {
			logger.error(AsyncTaskManager.addErrMessage(StringUtils.messageFormat("[{0}]页面部件模板解析失败：{1}#{2}",
					pw.getPublishPipeCode(), pw.getName(), pw.getCode())), e);
		}
	}

	@Override
	public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
