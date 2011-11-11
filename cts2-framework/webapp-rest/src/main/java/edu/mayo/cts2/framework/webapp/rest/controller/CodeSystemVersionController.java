/*
 * Copyright: (c) 2004-2011 Mayo Foundation for Medical Education and 
 * Research (MFMER). All rights reserved. MAYO, MAYO CLINIC, and the
 * triple-shield Mayo logo are trademarks and service marks of MFMER.
 *
 * Except as contained in the copyright notice above, or as used to identify 
 * MFMER as the author of this software, the trade names, trademarks, service
 * marks, or product names of the copyright holder shall not be used in
 * advertising, promotion or otherwise in connection with this software without
 * prior written authorization of the copyright holder.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.mayo.cts2.framework.webapp.rest.controller;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import edu.mayo.cts2.framework.model.codesystemversion.CodeSystemVersionCatalogEntry;
import edu.mayo.cts2.framework.model.codesystemversion.CodeSystemVersionCatalogEntryDirectory;
import edu.mayo.cts2.framework.model.codesystemversion.CodeSystemVersionCatalogEntryList;
import edu.mayo.cts2.framework.model.codesystemversion.CodeSystemVersionCatalogEntryMsg;
import edu.mayo.cts2.framework.model.codesystemversion.CodeSystemVersionCatalogEntrySummary;
import edu.mayo.cts2.framework.model.command.Page;
import edu.mayo.cts2.framework.model.command.ResolvedFilter;
import edu.mayo.cts2.framework.model.core.Message;
import edu.mayo.cts2.framework.model.directory.DirectoryResult;
import edu.mayo.cts2.framework.model.service.exception.UnknownCodeSystemVersion;
import edu.mayo.cts2.framework.model.updates.ChangeableResourceChoice;
import edu.mayo.cts2.framework.model.util.ModelUtils;
import edu.mayo.cts2.framework.service.command.restriction.CodeSystemVersionQueryServiceRestrictions;
import edu.mayo.cts2.framework.service.command.restriction.CodeSystemVersionQueryServiceRestrictions.EntityRestriction;
import edu.mayo.cts2.framework.service.profile.codesystemversion.CodeSystemVersionHistoryService;
import edu.mayo.cts2.framework.service.profile.codesystemversion.CodeSystemVersionMaintenanceService;
import edu.mayo.cts2.framework.service.profile.codesystemversion.CodeSystemVersionQueryService;
import edu.mayo.cts2.framework.service.profile.codesystemversion.CodeSystemVersionReadService;
import edu.mayo.cts2.framework.webapp.rest.command.QueryControl;
import edu.mayo.cts2.framework.webapp.rest.command.RestFilter;
import edu.mayo.cts2.framework.webapp.rest.command.RestReadContext;
import edu.mayo.cts2.framework.webapp.rest.naming.CodeSystemVersionNameResolver;

/**
 * The Class CodeSystemVersionController.
 *
 * @author <a href="mailto:kevin.peterson@mayo.edu">Kevin Peterson</a>
 */
@Controller
public class CodeSystemVersionController extends AbstractServiceAwareController {

	@Cts2Service
	private CodeSystemVersionReadService codeSystemVersionReadService;
	
	@Cts2Service
	private CodeSystemVersionQueryService codeSystemVersionQueryService;
	
	@Cts2Service
	private CodeSystemVersionMaintenanceService codeSystemVersionMaintenanceService;
	
	@Cts2Service
	private CodeSystemVersionHistoryService codeSystemVersionHistoryService;
	
	@Resource
	private CodeSystemVersionNameResolver codeSystemVersionNameResolver;
	
	private UrlTemplateBinder<CodeSystemVersionCatalogEntry> URL_BINDER = new 
			UrlTemplateBinder<CodeSystemVersionCatalogEntry>(){

		@Override
		public Map<String,String> getPathValues(CodeSystemVersionCatalogEntry resource) {
			Assert.notNull(resource.getVersionOf(),"'versionOf' is required.");
			
			Map<String,String> returnMap = new HashMap<String,String>();

			String codeSystemName = resource.getVersionOf().getContent();
			String codeSystemVersionName = resource.getCodeSystemVersionName();
			
			returnMap.put(VAR_CODESYSTEMID, codeSystemName);
			
			String versionId = 
					codeSystemVersionNameResolver.getVersionIdFromCodeSystemVersionName(
							codeSystemVersionReadService, 
							codeSystemVersionName);
							
			returnMap.put(VAR_CODESYSTEMVERSIONID, versionId);
			
			return returnMap;
		}

	};
	
	private final static MessageFactory<CodeSystemVersionCatalogEntry> MESSAGE_FACTORY = 
			new MessageFactory<CodeSystemVersionCatalogEntry>() {

		@Override
		public Message createMessage(CodeSystemVersionCatalogEntry resource) {
			CodeSystemVersionCatalogEntryMsg msg = new CodeSystemVersionCatalogEntryMsg();
			msg.setCodeSystemVersionCatalogEntry(resource);

			return msg;
		}
	};
	
	@RequestMapping(value=PATH_CODESYSTEMVERSION_CHANGEHISTORY, method=RequestMethod.GET)
	@ResponseBody
	public CodeSystemVersionCatalogEntryList getChangeHistory(
			HttpServletRequest httpServletRequest,
			@PathVariable(VAR_CODESYSTEMVERSIONID) String codeSystemVersionName,
			@RequestParam(required=false) Date PARAM_FROMDATE,
			@RequestParam(required=false) Date PARAM_TODATE,
			Page page) {
	
		DirectoryResult<CodeSystemVersionCatalogEntry> result = 
				this.codeSystemVersionHistoryService.getChangeHistory(
						ModelUtils.nameOrUriFromName(codeSystemVersionName),
						PARAM_FROMDATE,
						PARAM_TODATE);
		
		return this.populateDirectory(
				result, 
				page, 
				httpServletRequest, 
				CodeSystemVersionCatalogEntryList.class);	
	}
	
	@RequestMapping(value=PATH_CODESYSTEMVERSION_EARLIESTCHANGE, method=RequestMethod.GET)
	@ResponseBody
	public CodeSystemVersionCatalogEntryMsg getEarliesChange(
			HttpServletRequest httpServletRequest,
			@PathVariable(VAR_CODESYSTEMVERSIONID) String codeSystemVersionName) {
	
		CodeSystemVersionCatalogEntry result = 
				this.codeSystemVersionHistoryService.getEarliestChangeFor(ModelUtils.nameOrUriFromName(codeSystemVersionName));
		
		CodeSystemVersionCatalogEntryMsg msg = new CodeSystemVersionCatalogEntryMsg();
		msg.setCodeSystemVersionCatalogEntry(result);
		
		return this.wrapMessage(msg, httpServletRequest);
	}
	
	@RequestMapping(value=PATH_CODESYSTEMVERSION_LATESTCHANGE, method=RequestMethod.GET)
	@ResponseBody
	public CodeSystemVersionCatalogEntryMsg getLastChange(
			HttpServletRequest httpServletRequest,
			@PathVariable(VAR_CODESYSTEMVERSIONID) String codeSystemName) {
	
		CodeSystemVersionCatalogEntry result = 
				this.codeSystemVersionHistoryService.getLastChangeFor(ModelUtils.nameOrUriFromName(codeSystemName));
	
		CodeSystemVersionCatalogEntryMsg msg = new CodeSystemVersionCatalogEntryMsg();
		msg.setCodeSystemVersionCatalogEntry(result);
		
		return this.wrapMessage(msg, httpServletRequest);
	}
	
	/**
	 * Creates the code system version.
	 *
	 * @param changeseturi the changeseturi
	 * @param codeSystemVersion the code system version
	 * @param codeSystemName the code system name
	 * @param codeSystemVersionName the code system version name
	 */
	@RequestMapping(value=PATH_CODESYSTEMVERSION, method=RequestMethod.POST)
	public ResponseEntity<Void> createCodeSystemVersion(
			@RequestParam(required=false) String changeseturi,
			@RequestBody CodeSystemVersionCatalogEntry codeSystemVersion) {
			
		ChangeableResourceChoice choice = new ChangeableResourceChoice();
		choice.setCodeSystemVersion(codeSystemVersion);
		
		return this.getCreateHandler().create(
				choice, 
				changeseturi, 
				PATH_CODESYSTEMVERSION_OF_CODESYSTEM_BYID,
				URL_BINDER,
				this.codeSystemVersionMaintenanceService);
	}
	
	@RequestMapping(value=PATH_CODESYSTEMVERSION, method=RequestMethod.PUT)
	public void updateCodeSystemVersion(
			@RequestParam(required=false) String changeseturi,
			@RequestBody CodeSystemVersionCatalogEntry codeSystemVersion,
			@PathVariable(VAR_CODESYSTEMID) String codeSystemName,
			@PathVariable(VAR_CODESYSTEMVERSIONID) String codeSystemVersionName) {
			
		ChangeableResourceChoice choice = new ChangeableResourceChoice();
		choice.setCodeSystemVersion(codeSystemVersion);
		
		this.getUpdateHandler().update(
				choice, 
				changeseturi, 
				ModelUtils.nameOrUriFromName(codeSystemVersionName), 
				this.codeSystemVersionMaintenanceService);
	}
	
	/**
	 * Gets the code system versions of code system.
	 *
	 * @param httpServletRequest the http servlet request
	 * @param restrictions the restrictions
	 * @param resolvedFilter the filter
	 * @param page the page
	 * @param codeSystemId the code system id
	 * @return the code system versions of code system
	 */
	@RequestMapping(value={
			PATH_CODESYSTEMVERSIONS_OF_CODESYSTEM}, method=RequestMethod.GET)
	@ResponseBody
	public CodeSystemVersionCatalogEntryDirectory getCodeSystemVersionsOfCodeSystem(
			HttpServletRequest httpServletRequest,
			CodeSystemVersionQueryServiceRestrictions restrictions,
			RestFilter resolvedFilter,
			Page page,
			@PathVariable(VAR_CODESYSTEMID) String codeSystemName) {
		
		restrictions.setCodeSystem(ModelUtils.nameOrUriFromName(codeSystemName));
		
		ResolvedFilter filterComponent = this.processFilter(resolvedFilter, this.codeSystemVersionQueryService);
			
		DirectoryResult<CodeSystemVersionCatalogEntrySummary> directoryResult = 
			this.codeSystemVersionQueryService.getResourceSummaries(
					null, 
					createSet(filterComponent), 
					restrictions, 
					null, page);
		
		CodeSystemVersionCatalogEntryDirectory directory = this.populateDirectory(
				directoryResult, 
				page, 
				httpServletRequest, 
				CodeSystemVersionCatalogEntryDirectory.class);
		
		return directory;
	}
	
	/**
	 * Does code system version exist.
	 *
	 * @param httpServletResponse the http servlet response
	 * @param codeSystemName the code system name
	 * @param codeSystemVersionName the code system version name
	 */
	@RequestMapping(value=PATH_CODESYSTEMVERSION_OF_CODESYSTEM_BYID, method=RequestMethod.HEAD)
	@ResponseBody
	public void doesCodeSystemVersionExist(
			HttpServletResponse httpServletResponse,
			@PathVariable(VAR_CODESYSTEMID) String codeSystemName,
			@PathVariable(VAR_CODESYSTEMVERSIONID) String codeSystemVersionName) {
		
		this.doExists(
				httpServletResponse,
				this.codeSystemVersionReadService, 
				UnknownCodeSystemVersion.class,
				ModelUtils.nameOrUriFromName(codeSystemVersionName));
	}
	
	/**
	 * Gets the code system versions of code system count.
	 *
	 * @param httpServletResponse the http servlet response
	 * @param restrictions the restrictions
	 * @param resolvedFilter the filter
	 * @param codeSystemId the code system id
	 * @return the code system versions of code system count
	 */
	@RequestMapping(value={
			PATH_CODESYSTEMVERSIONS_OF_CODESYSTEM}, method=RequestMethod.HEAD)
	@ResponseBody
	public void getCodeSystemVersionsOfCodeSystemCount(
			HttpServletResponse httpServletResponse,
			CodeSystemVersionQueryServiceRestrictions restrictions,
			RestFilter resolvedFilter,
			@PathVariable(VAR_CODESYSTEMID) String codeSystemName) {
		restrictions.setCodeSystem(ModelUtils.nameOrUriFromName(codeSystemName));
		
		ResolvedFilter filterComponent = this.processFilter(resolvedFilter, this.codeSystemVersionQueryService);
		
		int count =
			this.codeSystemVersionQueryService.count(null, createSet(filterComponent), restrictions);
		
		this.setCount(count, httpServletResponse);
	}
	
	/**
	 * Gets the code system versions.
	 *
	 * @param httpServletRequest the http servlet request
	 * @param restrictions the restrictions
	 * @param resolvedFilter the filter
	 * @param page the page
	 * @return the code system versions
	 */
	@RequestMapping(value={
			PATH_CODESYSTEMVERSIONS}, method=RequestMethod.GET)
	@ResponseBody
	public CodeSystemVersionCatalogEntryDirectory getCodeSystemVersions(
			HttpServletRequest httpServletRequest,
			CodeSystemVersionQueryServiceRestrictions restrictions,
			RestFilter resolvedFilter,
			Page page) {
		
		ResolvedFilter filterComponent = this.processFilter(resolvedFilter, this.codeSystemVersionQueryService);
		
		DirectoryResult<CodeSystemVersionCatalogEntrySummary> directoryResult = 
			this.codeSystemVersionQueryService.getResourceSummaries(
					null, 
					createSet(filterComponent), 
					restrictions, 
					null, page);
		
		CodeSystemVersionCatalogEntryDirectory directory = this.populateDirectory(
				directoryResult, 
				page, 
				httpServletRequest, 
				CodeSystemVersionCatalogEntryDirectory.class);
		
		return directory;
	}
	
	/**
	 * Gets the code system versions count.
	 *
	 * @param httpServletResponse the http servlet response
	 * @param restrictions the restrictions
	 * @param resolvedFilter the filter
	 * @return the code system versions count
	 */
	@RequestMapping(value={
			PATH_CODESYSTEMVERSIONS}, method=RequestMethod.HEAD)
	@ResponseBody
	public void getCodeSystemVersionsCount(
			HttpServletResponse httpServletResponse,
			CodeSystemVersionQueryServiceRestrictions restrictions,
			RestFilter resolvedFilter) {
		
		ResolvedFilter filterComponent = this.processFilter(resolvedFilter, this.codeSystemVersionQueryService);
		
		int count =
			this.codeSystemVersionQueryService.count(null, createSet(filterComponent), restrictions);
		
		this.setCount(count, httpServletResponse);
	}
	
	/**
	 * Gets the code system version by name.
	 *
	 * @param httpServletRequest the http servlet request
	 * @param codeSystemName the code system name
	 * @param codeSystemVersionName the code system version name
	 * @return the code system version by name
	 */
	@RequestMapping(value={	
			PATH_CODESYSTEMVERSION_OF_CODESYSTEM_BYID
			},
		method=RequestMethod.GET)
	@ResponseBody
	public Message getCodeSystemVersionByNameOrOfficialResourceVersionId(
			HttpServletRequest httpServletRequest,
			RestReadContext restReadContext,
			QueryControl queryControl,
			@PathVariable(VAR_CODESYSTEMID) String codeSystemName,
			@PathVariable(VAR_CODESYSTEMVERSIONID) String versionId) {
		
		String codeSystemVersionName = 
				codeSystemVersionNameResolver.getCodeSystemVersionNameFromVersionId(
						this.codeSystemVersionReadService,
						codeSystemName, 
						versionId);
		
		Message msg = this.doRead(
					httpServletRequest, 
					MESSAGE_FACTORY, 
					this.codeSystemVersionReadService, 
					restReadContext,
					UnknownCodeSystemVersion.class,
					ModelUtils.nameOrUriFromName(codeSystemVersionName));
		
		return msg;
	}
	
	@RequestMapping(value={	
			PATH_CODESYSTEMVERSION_OF_CODESYSTEM_BYTAG
			},
		method=RequestMethod.GET)
	@ResponseBody
	public Message getCodeSystemVersionOfCodeSystemByTag(
			HttpServletRequest httpServletRequest,
			RestReadContext restReadContext,
			QueryControl queryControl,
			@PathVariable(VAR_CODESYSTEMID) String codeSystemName,
			@PathVariable(VAR_CODESYSTEMVERSIONID) String codeSystemVersionName) {
		
		//TODO: redirect this
		return this.doRead(
				httpServletRequest, 
				MESSAGE_FACTORY, 
				this.codeSystemVersionReadService, 
				restReadContext,
				UnknownCodeSystemVersion.class,
				ModelUtils.nameOrUriFromName(codeSystemVersionName));
	}
	
	@RequestMapping(value=PATH_CODESYSTEMVERSIONBYURI + "/**", method=RequestMethod.GET)
	public ModelAndView getCodeSystemVersionByUri(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse,
			QueryControl queryControl,
			@RequestParam(VAR_URI) String uri,
			@RequestParam(value="redirect", defaultValue="false") boolean redirect) {
	
		return this.doReadByUri(
				httpServletRequest, 
				MESSAGE_FACTORY, 
				PATH_CODESYSTEMVERSIONBYURI, 
				PATH_CODESYSTEMVERSION_OF_CODESYSTEM_BYID, 
				URL_BINDER, 
				this.codeSystemVersionReadService, 
				ModelUtils.nameOrUriFromUri(uri),
				redirect);
	}
	
	@RequestMapping(value=PATH_CODESYSTEMVERSIONQUERYSERVICE, method=RequestMethod.GET)
	@ResponseBody
	public Void getCodeSystemVersionCatalogQueryService() {
		return null;
		//TODO
	}
	
	@RequestMapping(value=PATH_CODESYSTEMVERSIONREADSERVICE, method=RequestMethod.GET)
	@ResponseBody
	public Void getCodeSystemVersionCatalogReadService() {
		return null;
		//TODO
	}
	
	
	@InitBinder
	 public void initCodeSystemRestrictionBinder(
			 WebDataBinder binder,
			 @RequestParam(value=PARAM_ENTITY, required=false) List<String> entity,
			 @RequestParam(value=PARAM_CODESYSTEM, required=false) String codesystem) {
		
		if(binder.getTarget() instanceof CodeSystemVersionQueryServiceRestrictions){
			CodeSystemVersionQueryServiceRestrictions restrictions = 
					(CodeSystemVersionQueryServiceRestrictions) binder.getTarget();
			
			if(StringUtils.isNotEmpty(codesystem)){
				restrictions.setCodeSystem(ModelUtils.nameOrUriFromEither(codesystem));
			}
			
			if(CollectionUtils.isNotEmpty(entity)){
				restrictions.setEntityRestriction(new EntityRestriction());

				restrictions.getEntityRestriction().setEntities(
						ControllerUtils.idsToEntityNameOrUriSet(entity));
			}		
		}
	 }

	public CodeSystemVersionReadService getCodeSystemVersionReadService() {
		return codeSystemVersionReadService;
	}

	public void setCodeSystemVersionReadService(
			CodeSystemVersionReadService codeSystemVersionReadService) {
		this.codeSystemVersionReadService = codeSystemVersionReadService;
	}

	public CodeSystemVersionQueryService getCodeSystemVersionQueryService() {
		return codeSystemVersionQueryService;
	}

	public void setCodeSystemVersionQueryService(
			CodeSystemVersionQueryService codeSystemVersionQueryService) {
		this.codeSystemVersionQueryService = codeSystemVersionQueryService;
	}

	public CodeSystemVersionMaintenanceService getCodeSystemVersionMaintenanceService() {
		return codeSystemVersionMaintenanceService;
	}

	public void setCodeSystemVersionMaintenanceService(
			CodeSystemVersionMaintenanceService codeSystemVersionMaintenanceService) {
		this.codeSystemVersionMaintenanceService = codeSystemVersionMaintenanceService;
	}

	public CodeSystemVersionHistoryService getCodeSystemVersionHistoryService() {
		return codeSystemVersionHistoryService;
	}

	public void setCodeSystemVersionHistoryService(
			CodeSystemVersionHistoryService codeSystemVersionHistoryService) {
		this.codeSystemVersionHistoryService = codeSystemVersionHistoryService;
	}

	public CodeSystemVersionNameResolver getCodeSystemVersionNameResolver() {
		return codeSystemVersionNameResolver;
	}

	public void setCodeSystemVersionNameResolver(
			CodeSystemVersionNameResolver codeSystemVersionNameResolver) {
		this.codeSystemVersionNameResolver = codeSystemVersionNameResolver;
	}
}