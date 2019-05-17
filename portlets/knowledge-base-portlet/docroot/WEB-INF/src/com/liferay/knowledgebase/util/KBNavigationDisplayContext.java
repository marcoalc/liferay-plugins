/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.knowledgebase.util;

import com.liferay.knowledgebase.model.KBArticle;
import com.liferay.knowledgebase.model.KBFolder;
import com.liferay.knowledgebase.model.KBFolderConstants;
import com.liferay.knowledgebase.service.KBArticleLocalServiceUtil;
import com.liferay.knowledgebase.service.KBArticleServiceUtil;
import com.liferay.knowledgebase.service.KBFolderServiceUtil;
import com.liferay.knowledgebase.util.comparator.KBArticlePriorityComparator;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portlet.PortalPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.portlet.PortletPreferences;
import javax.portlet.PortletRequest;

/**
 * @author Adolfo Pérez
 */
public class KBNavigationDisplayContext {

	public KBNavigationDisplayContext(
		PortletRequest portletRequest, PortalPreferences portalPreferences,
		PortletPreferences portletPreferences, KBArticle kbArticle) {

		_portletRequest = portletRequest;
		_portalPreferences = portalPreferences;
		_portletPreferences = portletPreferences;
		_kbArticle = kbArticle;
	}

	public List<Long> getAncestorResourcePrimaryKeys()
		throws PortalException, SystemException {

		List<Long> ancestorResourcePrimaryKeys = new ArrayList<Long>();

		if (_kbArticle != null) {
			KBArticle latestKBArticle =
				KBArticleLocalServiceUtil.getLatestKBArticle(
					_kbArticle.getResourcePrimKey(),
					WorkflowConstants.STATUS_APPROVED);

			ancestorResourcePrimaryKeys =
				latestKBArticle.getAncestorResourcePrimaryKeys();

			Collections.reverse(ancestorResourcePrimaryKeys);
		}
		else {
			ancestorResourcePrimaryKeys.add(
				KBFolderConstants.DEFAULT_PARENT_FOLDER_ID);
		}

		return ancestorResourcePrimaryKeys;
	}

	public List<KBArticle> getChildKBArticles(
			long groupId, long parentResourcePrimKey, int level)
		throws PortalException, SystemException {

		if ((parentResourcePrimKey == getResourcePrimKey()) && (level == 0) &&
			!isFolderResource()) {

			KBArticle kbArticle = KBArticleServiceUtil.getLatestKBArticle(
				getResourcePrimKey(), WorkflowConstants.STATUS_APPROVED);

			return Collections.singletonList(kbArticle);
		}

		boolean maxNestingLevelReached = isMaxNestingLevelReached(level);

		List<KBArticle> childKBArticles;

		if (maxNestingLevelReached) {
			childKBArticles = KBArticleServiceUtil.getAllDescendantKBArticles(
				groupId, parentResourcePrimKey,
				WorkflowConstants.STATUS_APPROVED,
				new KBArticlePriorityComparator(true));
		}
		else {
			childKBArticles = KBArticleServiceUtil.getKBArticles(
				groupId, parentResourcePrimKey,
				WorkflowConstants.STATUS_APPROVED, QueryUtil.ALL_POS,
				QueryUtil.ALL_POS, new KBArticlePriorityComparator(true));
		}

		return childKBArticles;
	}

	public String getCurrentKBFolderURLTitle()
		throws PortalException, SystemException {

		String currentKBFolderURLTitle =
			KnowledgeBaseUtil.getPreferredKBFolderURLTitle(
				_portalPreferences, getContentRootPrefix());

		long rootResourcePrimKey = getRootResourcePrimKey();

		if (rootResourcePrimKey != KBFolderConstants.DEFAULT_PARENT_FOLDER_ID) {
			KBFolder kbFolder = KBFolderServiceUtil.getKBFolder(
				rootResourcePrimKey);

			currentKBFolderURLTitle = kbFolder.getUrlTitle();
		}

		return currentKBFolderURLTitle;
	}

	public String getPageTitle() throws PortalException, SystemException {
		long rootResourcePrimKey = getRootResourcePrimKey();

		String pageTitle = null;

		if (isFolderResource() &&
			(rootResourcePrimKey !=
					KBFolderConstants.DEFAULT_PARENT_FOLDER_ID)) {

			KBFolder kbFolder = KBFolderServiceUtil.getKBFolder(
				rootResourcePrimKey);

			pageTitle = getContentRootPrefix() + " " + kbFolder.getName();

			if (_kbArticle != null) {
				pageTitle = _kbArticle.getTitle() + " - " + pageTitle;
			}
		}
		else {
			if (_kbArticle != null) {
				pageTitle = _kbArticle.getTitle();
			}
		}

		return pageTitle;
	}

	public long getParentResourcePrimKey()
		throws PortalException, SystemException {

		if (_kbArticle != null) {
			return _kbArticle.getParentResourcePrimKey();
		}

		return getRootResourcePrimKey();
	}

	public long getRootResourcePrimKey()
		throws PortalException, SystemException {

		if (_rootResourcePrimKey == null) {
			if (!isFolderResource()) {
				_rootResourcePrimKey = getResourcePrimKey();
			}
			else if (_kbArticle != null) {
				_rootResourcePrimKey = KnowledgeBaseUtil.getKBFolderId(
					_kbArticle.getParentResourceClassNameId(),
					_kbArticle.getParentResourcePrimKey());
			}
			else {
				_rootResourcePrimKey = KnowledgeBaseUtil.getRootResourcePrimKey(
					_portletRequest,
					PortalUtil.getScopeGroupId(_portletRequest),
					getResourceClassNameId(), getResourcePrimKey());
			}
		}

		return _rootResourcePrimKey;
	}

	public boolean isChildKBArticleExpanded(KBArticle childKBArticle, int level)
		throws PortalException, SystemException {

		List<Long> ancestorResourcePrimaryKeys =
			getAncestorResourcePrimaryKeys();

		if ((ancestorResourcePrimaryKeys.size() > 1) &&
			(level < ancestorResourcePrimaryKeys.size()) &&
			(childKBArticle.getResourcePrimKey() ==
				ancestorResourcePrimaryKeys.get(level))) {

			return true;
		}

		return false;
	}

	public boolean isFolderResource() {
		long kbFolderClassNameId = PortalUtil.getClassNameId(
			KBFolderConstants.getClassName());

		if (kbFolderClassNameId == getResourceClassNameId()) {
			return true;
		}

		return false;
	}

	public boolean isFurtherExpansionRequired(
			long parentResourcePrimKey, KBArticle childKBArticle, int level)
		throws PortalException, SystemException {

		List<Long> ancestorResourcePrimaryKeys =
				getAncestorResourcePrimaryKeys();

			if (!isMaxNestingLevelReached(level) &&
				ancestorResourcePrimaryKeys.contains(
					childKBArticle.getResourcePrimKey())) {

				return true;
			}

			return false;
	}

	public boolean isLeftNavigationVisible()
		throws PortalException, SystemException {

		if (_leftNavigationVisible == null) {
			_leftNavigationVisible = hasMultipleDescendantKBArticles();
		}

		return _leftNavigationVisible;
	}

	public boolean isMaxNestingLevelReached(int level) {
		int maxNestingLevel = GetterUtil.getInteger(
			_portletPreferences.getValue("maxNestingLevel", null), 3);

		boolean maxNestingLevelReached = false;

		if ((maxNestingLevel - level) <= 1) {
			maxNestingLevelReached = true;
		}

		return maxNestingLevelReached;
	}

	public boolean isTopNavigationVisible()
		throws PortalException, SystemException {

		if (isFolderResource() && !isLeftNavigationVisible()) {
			return true;
		}

		return false;
	}

	protected String getContentRootPrefix() {
		return GetterUtil.getString(
			_portletPreferences.getValue("contentRootPrefix", null));
	}

	protected long getResourceClassNameId() {
		if (_resourceClassNameId == null) {
			_resourceClassNameId = GetterUtil.getLong(
				_portletPreferences.getValue("resourceClassNameId", null),
				PortalUtil.getClassNameId(KBFolderConstants.getClassName()));
		}

		return _resourceClassNameId;
	}

	protected long getResourcePrimKey() {
		if (_resourcePrimKey == null) {
			_resourcePrimKey = GetterUtil.getLong(
				_portletPreferences.getValue("resourcePrimKey", null));
		}

		return _resourcePrimKey;
	}

	protected boolean hasMultipleDescendantKBArticles()
		throws PortalException, SystemException {

		long scopeGroupId = PortalUtil.getScopeGroupId(_portletRequest);

		if (isFolderResource()) {
			List<KBFolder> kbFolders =
				KnowledgeBaseUtil.getAlternateRootKBFolders(
					scopeGroupId, getResourcePrimKey());

			if (kbFolders.size() > 1) {
				int maxKBArticlesCount = 0;

				for (KBFolder kbFolder : kbFolders) {
					int kbArticlesCount =
						KBArticleLocalServiceUtil.getKBFolderKBArticlesCount(
							scopeGroupId, kbFolder.getKbFolderId(),
							WorkflowConstants.STATUS_APPROVED);

					if (kbArticlesCount > maxKBArticlesCount) {
						maxKBArticlesCount = kbArticlesCount;
					}
				}

				if (maxKBArticlesCount > 1) {
					return true;
				}

				return false;
			}
		}

		long rootResourcePrimKey = getRootResourcePrimKey();

		int kbArticlesCount = KBArticleLocalServiceUtil.getKBArticlesCount(
			scopeGroupId, rootResourcePrimKey,
			WorkflowConstants.STATUS_APPROVED);

		if (!isFolderResource()) {
			kbArticlesCount++;
		}

		if (kbArticlesCount == 0) {
			return false;
		}

		if (kbArticlesCount != 1) {
			return true;
		}

		List<KBArticle> kbArticles = KBArticleLocalServiceUtil.getKBArticles(
			scopeGroupId, rootResourcePrimKey,
			WorkflowConstants.STATUS_APPROVED, 0, 1, null);

		if (kbArticles.isEmpty()) {
			return false;
		}

		KBArticle navigationKBArticle = kbArticles.get(0);

		int navigationKBArticleChildCount =
			KBArticleLocalServiceUtil.getKBArticlesCount(
				scopeGroupId, navigationKBArticle.getResourcePrimKey(),
				WorkflowConstants.STATUS_APPROVED);

		if (navigationKBArticleChildCount == 0) {
			return false;
		}

		return true;
	}

	private final KBArticle _kbArticle;
	private Boolean _leftNavigationVisible;
	private final PortalPreferences _portalPreferences;
	private final PortletPreferences _portletPreferences;
	private final PortletRequest _portletRequest;
	private Long _resourceClassNameId;
	private Long _resourcePrimKey;
	private Long _rootResourcePrimKey;

}