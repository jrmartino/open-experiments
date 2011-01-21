/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.nakamura.user.servlet;

import static org.sakaiproject.nakamura.api.lite.authorizable.Group.EVERYONE_GROUP;
import static org.sakaiproject.nakamura.api.user.UserConstants.PROP_GROUP_MANAGERS;
import static org.sakaiproject.nakamura.api.user.UserConstants.PROP_GROUP_VIEWERS;
import static org.sakaiproject.nakamura.api.user.UserConstants.SYSTEM_USER_MANAGER_GROUP_PREFIX;

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.core.security.SecurityConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.apache.sling.servlets.post.impl.helper.RequestProperty;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceExtension;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceParameter;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.api.doc.ServiceSelector;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.user.AuthorizablePostProcessService;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.user.NameSanitizer;
import org.sakaiproject.nakamura.util.osgi.EventUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

/**
 * <p>
 * Sling Post Servlet implementation for creating a group in the jackrabbit UserManager.
 * </p>
 * <h2>Rest Service Description</h2>
 * <p>
 * Creates a new group. Maps on to nodes of resourceType <code>sling/groups</code> like
 * <code>/rep:system/rep:userManager/rep:groups</code> mapped to a resource url
 * <code>/system/userManager/group</code>. This servlet responds at
 * <code>/system/userManager/group.create.html</code>
 * </p>
 * <h4>Methods</h4>
 * <ul>
 * <li>POST</li>
 * </ul>
 * <h4>Post Parameters</h4>
 * <dl>
 * <dt>:name</dt>
 * <dd>The name of the new group (required)</dd>
 * <dt>*</dt>
 * <dd>Any additional parameters become properties of the group node (optional)</dd>
 * </dl>
 * <h4>Response</h4>
 * <dl>
 * <dt>200</dt>
 * <dd>Success, a redirect is sent to the group resource locator. The redirect comes with
 * HTML describing the status.</dd>
 * <dt>500</dt>
 * <dd>Failure, including group already exists. HTML explains the failure.</dd>
 * </dl>
 * <h4>Example</h4>
 * 
 * <code>
 * curl -F:name=newGroupA  -Fproperty1=value1 http://localhost:8080/system/userManager/group.create.html
 * </code>
 * 
 * <h4>Notes</h4>
 * 
 * @scr.component immediate="true"
 * @scr.service interface="javax.servlet.Servlet"
 * @scr.property name="sling.servlet.resourceTypes" value="sling/groups"
 * @scr.property name="sling.servlet.methods" value="POST"
 * @scr.property name="sling.servlet.selectors" value="create"
 * 
 * @scr.property name="servlet.post.dateFormats"
 *               values.0="EEE MMM dd yyyy HH:mm:ss 'GMT'Z"
 *               values.1="yyyy-MM-dd'T'HH:mm:ss.SSSZ" values.2="yyyy-MM-dd'T'HH:mm:ss"
 *               values.3="yyyy-MM-dd" values.4="dd.MM.yyyy HH:mm:ss"
 *               values.5="dd.MM.yyyy"
 * 
 * 
 */
@ServiceDocumentation(name = "Create Group Servlet", description = "Creates a new group. Maps on to nodes of resourceType sling/groups like "
    + "/rep:system/rep:userManager/rep:groups mapped to a resource url "
    + "/system/userManager/group. This servlet responds at /system/userManager/group.create.html", shortDescription = "Creates a new group", bindings = @ServiceBinding(type = BindingType.PATH, bindings = "/system/userManager/group.create.html", selectors = @ServiceSelector(name = "create", description = "Creates a new group"), extensions = @ServiceExtension(name = "html", description = "Posts produce html containing the update status")), methods = @ServiceMethod(name = "POST", description = {
    "Creates a new group with a name :name, "
        + "storing additional parameters as properties of the new group.",
    "Example<br>"
        + "<pre>curl -F:name=g-groupname -Fproperty1=value1 http://localhost:8080/system/userManager/group.create.html</pre>" }, parameters = {
    @ServiceParameter(name = ":name", description = "The name of the new group (required)"),
    @ServiceParameter(name = "", description = "Additional parameters become group node properties, "
        + "except for parameters starting with ':', which are only forwarded to post-processors (optional)") }, response = {
    @ServiceResponse(code = 200, description = "Success, a redirect is sent to the groups resource locator with HTML describing status."),
    @ServiceResponse(code = 500, description = "Failure, including group already exists. HTML explains failure.") }))
public class CreateSakaiGroupServlet extends AbstractSakaiGroupPostServlet implements
    ManagedService {

  /**
   *
   */
  private static final long serialVersionUID = 6587376522316825454L;

  private static final Logger LOGGER = LoggerFactory
      .getLogger(CreateSakaiGroupServlet.class);

  /**
   * The Sparse Repository we access to resolve resources
   * 
   * @scr.reference
   */
  protected transient Repository repository;

  /**
   * Used to launch OSGi events.
   * 
   * @scr.reference
   */
  protected transient EventAdmin eventAdmin;

  /**
   * Used to create the group.
   * 
   * @scr.reference
   */
  protected transient AuthorizablePostProcessService postProcessorService;

  /**
   * 
   * @scr.property value="authenticated,everyone" type="String"
   *               name="Groups who are allowed to create other groups" description=
   *               "A comma separated list of groups who area allowed to create other groups"
   */
  public static final String GROUP_AUTHORISED_TOCREATE = "groups.authorized.tocreate";

  private String[] authorizedGroups = { "authenticated" };

  /*
   * (non-Javadoc)
   * 
   * @seeorg.apache.sling.jackrabbit.usermanager.post.AbstractAuthorizablePostServlet#
   * handleOperation(org.apache.sling.api.SlingHttpServletRequest,
   * org.apache.sling.api.servlets.HtmlResponse, java.util.List)
   */
  @Override
  @edu.umd.cs.findbugs.annotations.SuppressWarnings(justification = "If there is an exception, the user is certainly not admin", value = { "REC_CATCH_EXCEPTION" })
  protected void handleOperation(SlingHttpServletRequest request, HtmlResponse response,
      List<Modification> changes) throws ServletException {

    // KERN-432 dont allow anon users to access create group.
    if (SecurityConstants.ANONYMOUS_ID.equals(request.getRemoteUser())) {
      response.setStatus(403, "AccessDenied");
      return;
    }

    // check that the submitted parameter values have valid values.
    final String principalName = request.getParameter(SlingPostConstants.RP_NODE_NAME);
    if (principalName == null) {
      throw new ServletException("Group name was not submitted");
    }

    NameSanitizer san = new NameSanitizer(principalName, false);
    san.validate();

    // check for allow create Group
    boolean allowCreateGroup = false;
    User currentUser = null;

    try {
      final Session currentSession = StorageClientUtils.adaptToSession(request
          .getResourceResolver().adaptTo(javax.jcr.Session.class));
      AuthorizableManager um = currentSession.getAuthorizableManager();
      currentUser = (User) um.findAuthorizable(currentSession.getUserId());
      if (currentUser.isAdmin()) {
        LOGGER.debug("User is an admin ");
        allowCreateGroup = true;
      } else {
        LOGGER.debug("Checking for membership of one of {} ",
            Arrays.toString(authorizedGroups));
        String[] groupMemberships = getGroupMembership(currentUser);

        for (String groupName : authorizedGroups) {
          if (Arrays.asList(groupMemberships).contains(groupName)) {
            allowCreateGroup = true;
            break;
          }

          // TODO: move this nasty hack into the PrincipalManager dynamic groups need to
          // be in the principal manager for this to work.
          if ("authenticated".equals(groupName)
              && !SecurityConstants.ADMIN_ID.equals(currentUser.getId())) {
            allowCreateGroup = true;
            break;
          }

          // just check via the user manager for dynamic resolution.
          Group group = (Group) um.findAuthorizable(groupName);
          LOGGER.debug("Checking for group  {} {} ", groupName, group);
          final List<String> groupMembers = Arrays.asList(group.getMembers());
          if (group != null && groupMembers.contains(currentUser.getId())) {
            allowCreateGroup = true;
            LOGGER.debug("User is a member  of {} {} ", groupName, group);
            break;
          }
        }
      }
    } catch (Exception ex) {
      LOGGER.warn("Failed to determin if the user is an admin, assuming not. Cause: "
          + ex.getMessage());
      allowCreateGroup = false;
    }

    if (!allowCreateGroup) {
      LOGGER.debug("User is not allowed to create groups ");
      response.setStatus(HttpServletResponse.SC_FORBIDDEN,
          "User is not allowed to create groups");
      return;
    }

    Session adminSession = getAdminSession();

    try {
      AuthorizableManager userManager = adminSession.getAuthorizableManager();
      Authorizable authorizable = userManager.findAuthorizable(principalName);

      if (authorizable != null) {
        // principal already exists!
        response.setStatus(400, "A principal already exists with the requested name: "
            + principalName);
        return;
      } else {
        // TODO BL120 - create group with empty properties for now and fill later?
        final Map<String, Object> emptyProperties = Collections.emptyMap();
        userManager.createGroup(principalName, principalName, emptyProperties);
        Group group = (Group) userManager.findAuthorizable(principalName);
        final String groupPath = SYSTEM_USER_MANAGER_GROUP_PREFIX + group.getId();
        Map<String, RequestProperty> reqProperties = collectContent(request, response,
            groupPath);

        response.setPath(groupPath);
        response.setLocation(externalizePath(request, groupPath));
        response.setParentLocation(externalizePath(request,
            UserConstants.SYSTEM_USER_MANAGER_GROUP_PATH));
        changes.add(Modification.onCreated(groupPath));

        // It is not allowed to touch the rep:group-managers property directly.
        String key = SYSTEM_USER_MANAGER_GROUP_PREFIX + principalName + "/";
        reqProperties.remove(key + PROP_GROUP_MANAGERS);
        reqProperties.remove(key + PROP_GROUP_VIEWERS);

        // write content from form
        writeContent(adminSession, group, reqProperties, changes);

        // update the group memberships, although this uses session from the request, it
        // only
        // does so for finding authorizables, so its ok that we are using an admin session
        // here.
        updateGroupMembership(request, group, changes);
        // TODO We should probably let the client decide whether the
        // current user belongs in the managers list or not.
        updateOwnership(request, group, new String[] { currentUser.getId() }, changes);

        try {
          postProcessorService.process(group, adminSession, ModificationType.CREATE,
              request);
        } catch (ServletException e) {
          LOGGER.info("Failed to create Group  {}", e.getMessage());
          response.setStatus(HttpServletResponse.SC_CONFLICT, e.getMessage());
          return;
        } catch (Exception e) {
          LOGGER.warn(e.getMessage(), e);
          response
              .setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
          return;
        }

        // save the modified group
        userManager.updateAuthorizable(group);

        // Launch an OSGi event for creating a group.
        try {
          Dictionary<String, String> properties = new Hashtable<String, String>();
          properties.put(UserConstants.EVENT_PROP_USERID, principalName);
          EventUtils.sendOsgiEvent(properties, UserConstants.TOPIC_GROUP_CREATED,
              eventAdmin);
        } catch (Exception e) {
          // Trap all exception so we don't disrupt the normal behaviour.
          LOGGER.error("Failed to launch an OSGi event for creating a user.", e);
        }
      }
    } catch (ServletException re) {
      LOGGER.info("Failed to create Group  {}", re.getMessage());
      LOGGER.debug("Failed to create Group Cause {}", re, re.getMessage());
      response.setStatus(HttpServletResponse.SC_CONFLICT, re.getMessage());
      return;
    } catch (StorageClientException e) {
      LOGGER.info("Failed to create Group  {}", e.getMessage());
      LOGGER.debug("Failed to create Group Cause {}", e, e.getMessage());
      response.setStatus(HttpServletResponse.SC_CONFLICT, e.getMessage());
      return;
    } catch (AccessDeniedException e) {
      LOGGER.info("Failed to create Group  {}", e.getMessage());
      LOGGER.debug("Failed to create Group Cause {}", e, e.getMessage());
      response.setStatus(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
      return;
    } finally {
      ungetSession(adminSession);
    }
  }

  // ---------- SCR integration ---------------------------------------------

  /**
   * Activates this component.
   * 
   * @param componentContext
   *          The OSGi <code>ComponentContext</code> of this component.
   */
  @Override
  protected void activate(ComponentContext componentContext) {
    super.activate(componentContext);
    String groupList = (String) componentContext.getProperties().get(
        GROUP_AUTHORISED_TOCREATE);
    if (groupList != null) {
      authorizedGroups = StringUtils.split(groupList, ',');
    }
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.osgi.service.cm.ManagedService#updated(java.util.Dictionary)
   */
  @SuppressWarnings("rawtypes")
  public void updated(Dictionary dictionary) throws ConfigurationException {
    String groupList = (String) dictionary.get(GROUP_AUTHORISED_TOCREATE);
    if (groupList != null) {
      authorizedGroups = StringUtils.split(groupList, ',');
    }
  }

  public String[] getGroupMembership(final Authorizable a) {

    final List<String> memberIds = new ArrayList<String>();
    boolean anonymous = true;
    if (a != null) {
      Collections.addAll(memberIds, a.getPrincipals());
      if (!User.ANON_USER.equals(a.getId())) {
        anonymous = false;
      }
    } else { // could not find az
      return null;
    }
    // add the everyone group if it is not there already
    if (!anonymous && !memberIds.contains(EVERYONE_GROUP.getId())) {
      memberIds.add(EVERYONE_GROUP.getId());
    }
    return (String[]) memberIds.toArray();
  }

  /**
   * @param repository
   *          the repository to set
   */
  public void setRepository(Repository repository) {
    if (repository == null) {
      throw new IllegalArgumentException("repository == null");
    }
    super.repository = repository;
    this.repository = repository;
  }
}
