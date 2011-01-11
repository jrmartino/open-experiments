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

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.apache.sling.servlets.post.impl.helper.RequestProperty;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.auth.trusted.RequestTrustValidator;
import org.sakaiproject.nakamura.api.auth.trusted.RequestTrustValidatorService;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceExtension;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceParameter;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.api.doc.ServiceSelector;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.user.AuthorizablePostProcessService;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.user.NameSanitizer;
import org.sakaiproject.nakamura.util.osgi.EventUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletResponse;

/**
 * <p>
 * Sling Post Servlet implementation for creating a user in the jackrabbit UserManager.
 * </p>
 * <h2>Rest Service Description</h2>
 * <p>
 * Creates a new user. Maps on to nodes of resourceType <code>sling/users</code> like
 * <code>/rep:system/rep:userManager/rep:users</code> mapped to a resource url
 * <code>/system/userManager/user</code>. This servlet responds at
 * <code>/system/userManager/user.create.html</code>
 * </p>
 * <h4>Methods</h4>
 * <ul>
 * <li>POST</li>
 * </ul>
 * <h4>Post Parameters</h4>
 * <dl>
 * <dt>:name</dt>
 * <dd>The name of the new user (required)</dd>
 * <dt>:pwd</dt>
 * <dd>The password of the new user (required)</dd>
 * <dt>:pwdConfirm</dt>
 * <dd>The password of the new user (required)</dd>
 * <dt>*</dt>
 * <dd>Any additional parameters become properties of the user node (optional)</dd>
 * </dl>
 * <h4>Response</h4>
 * <dl>
 * <dt>200</dt>
 * <dd>Success, a redirect is sent to the users resource locator. The redirect comes with
 * HTML describing the status.</dd>
 * <dt>500</dt>
 * <dd>Failure, including user already exists. HTML explains the failure.</dd>
 * </dl>
 * <h4>Example</h4>
 * 
 * <code>
 * curl -F:name=ieb -Fpwd=password -FpwdConfirm=password -Fproperty1=value1 http://localhost:8080/system/userManager/user.create.html
 * </code>
 * 
 * 
 * @scr.component immediate="true" label="%createUser.post.operation.name"
 *                description="%createUser.post.operation.description"
 * @scr.service interface="javax.servlet.Servlet"
 * @scr.property name="sling.servlet.resourceTypes" value="sling/users"
 * @scr.property name="sling.servlet.methods" value="POST"
 * @scr.property name="sling.servlet.selectors" value="create"
 * 
 * @scr.property name="password.digest.algorithm" value="sha1"
 * 
 * 
 * @scr.property name="servlet.post.dateFormats"
 *               values.0="EEE MMM dd yyyy HH:mm:ss 'GMT'Z"
 *               values.1="yyyy-MM-dd'T'HH:mm:ss.SSSZ" values.2="yyyy-MM-dd'T'HH:mm:ss"
 *               values.3="yyyy-MM-dd" values.4="dd.MM.yyyy HH:mm:ss"
 *               values.5="dd.MM.yyyy"
 * 
 * 
 * @scr.property name="self.registration.enabled" label="%self.registration.enabled.name"
 *               description="%self.registration.enabled.description"
 *               valueRef="DEFAULT_SELF_REGISTRATION_ENABLED"
 * 
 */

@ServiceDocumentation(name = "Create User Servlet", description = "Creates a new user. Maps on to nodes of resourceType sling/users like "
    + "/rep:system/rep:userManager/rep:users mapped to a resource url /system/userManager/user. "
    + "This servlet responds at /system/userManager/user.create.html", shortDescription = "Creates a new user", bindings = @ServiceBinding(type = BindingType.PATH, bindings = "/system/userManager", selectors = { @ServiceSelector(name = "create", description = "binds to this servlet for user creation") }, extensions = { @ServiceExtension(name = "html", description = "All post operations produce HTML") }),

methods = @ServiceMethod(name = "POST", description = {
    "Creates a new user with a name :name, and password pwd, "
        + "storing additional parameters as properties of the new user.",
    "Example<br><pre>curl -F:name=username -Fpwd=password -FpwdConfirm=password "
        + "-Fproperty1=value1 http://localhost:8080/system/userManager/user.create.html</pre>" }, parameters = {
    @ServiceParameter(name = ":name", description = "The name of the new user (required)"),
    @ServiceParameter(name = "pwd", description = "The password of the new user (required)"),
    @ServiceParameter(name = "pwdConfirm", description = "The password of the new user (required)"),
    @ServiceParameter(name = "", description = "Additional parameters become user node properties, "
        + "except for parameters starting with ':', which are only forwarded to post-processors (optional)"),
    @ServiceParameter(name = ":create-auth", description = "The name of a per request authentication "
        + "mechanism eg capatcha, callers will also need to add parameters to satisfy the "
        + "authentication method,  (optional)") }, response = {
    @ServiceResponse(code = 200, description = "Success, a redirect is sent to the users resource locator with HTML describing status."),
    @ServiceResponse(code = 400, description = "Failure, when you try to create a user with a username that already exists."),
    @ServiceResponse(code = 500, description = "Failure, HTML explains failure.") }))
public class CreateSakaiUserServlet extends AbstractUserPostServlet {

  private static final long serialVersionUID = -5060795742204221361L;

  /**
   * default log
   */
  private static final Logger log = LoggerFactory.getLogger(CreateSakaiUserServlet.class);

  private static final String PROP_SELF_REGISTRATION_ENABLED = "self.registration.enabled";

  private static final Boolean DEFAULT_SELF_REGISTRATION_ENABLED = Boolean.TRUE;

  private Boolean selfRegistrationEnabled = DEFAULT_SELF_REGISTRATION_ENABLED;

  /**
   * The sparse Repository we access to resolve resources
   * 
   * @scr.reference
   */
  private transient Repository repository;

  /**
   * Used to launch OSGi events.
   * 
   * @scr.reference
   */
  protected transient EventAdmin eventAdmin;

  /**
   * Used to post process authorizable creation request.
   * 
   * @scr.reference
   */
  private transient AuthorizablePostProcessService postProcessorService;

  private String adminUserId = null;

  private Object lock = new Object();

  /**
   * 
   * @scr.reference
   */
  protected transient RequestTrustValidatorService requestTrustValidatorService;

  /**
   * Returns an administrative session to the default workspace.
   * 
   * @throws AccessDeniedException
   * @throws StorageClientException
   * @throws ClientPoolException
   */
  private Session getAdminSession() throws ClientPoolException, StorageClientException,
      AccessDeniedException {
    return repository.loginAdministrative();
  }

  /**
   * Return the administrative session and close it.
   * 
   * @throws ClientPoolException
   */
  private void ungetSession(final Session session) {
    try {
      session.logout();
    } catch (ClientPoolException e) {
      log.error("Could not log out of session");
      throw new IllegalStateException(e);
    }
  }

  // ---------- SCR integration ---------------------------------------------

  /**
   * Activates this component.
   * 
   * @param componentContext
   *          The OSGi <code>ComponentContext</code> of this component.
   */
  protected void activate(ComponentContext componentContext) {
    super.activate(componentContext);
    Dictionary<?, ?> props = componentContext.getProperties();
    Object propValue = props.get(PROP_SELF_REGISTRATION_ENABLED);
    if (propValue instanceof String) {
      selfRegistrationEnabled = Boolean.parseBoolean((String) propValue);
    } else {
      selfRegistrationEnabled = DEFAULT_SELF_REGISTRATION_ENABLED;
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.sling.jackrabbit.usermanager.post.AbstractAuthorizablePostServlet
   * #handleOperation(org.apache.sling.api.SlingHttpServletRequest,
   * org.apache.sling.api.servlets.HtmlResponse, java.util.List)
   */
  @Override
  protected void handleOperation(SlingHttpServletRequest request, HtmlResponse response,
      List<Modification> changes) throws RepositoryException {

    // check for an administrator
    boolean administrator = false;
    try {
      Session currentSession = request.getResourceResolver().adaptTo(Session.class);
      /*
       * LDS: This next block of code that sets member variable "adminUserId" looks buggy
       * to me. Why not just set adminUserId = User.ADMIN_USER and be done with it? I
       * don't understand why the current user must be admin before this variable get
       * initialized.
       */
      if (adminUserId == null) {
        synchronized (lock) {
          administrator = User.ADMIN_USER.equals(currentSession.getUserId());
          if (administrator) {
            adminUserId = User.ADMIN_USER;
          }
        }
      } else {
        administrator = adminUserId.equals(currentSession.getUserId());
      }
    } catch (Exception ex) {
      log.warn("Failed to determin if the user is an admin, assuming not. Cause: "
          + ex.getMessage());
      administrator = false;
    }
    if (!administrator) {
      if (!selfRegistrationEnabled) {
        throw new RepositoryException(
            "Sorry, registration of new users is not currently enabled. Please try again later.");
      }

      boolean trustedRequest = false;
      String trustMechanism = request.getParameter(":create-auth");
      if (trustMechanism != null) {
        RequestTrustValidator validator = requestTrustValidatorService
            .getValidator(trustMechanism);
        if (validator != null
            && validator.getLevel() >= RequestTrustValidator.CREATE_USER
            && validator.isTrusted(request)) {
          trustedRequest = true;
        }
      }

      if (selfRegistrationEnabled && !trustedRequest) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED, "Untrusted request.");
        log.error("Untrusted request.");
        return;
      }
    }

    Session session = request.getResourceResolver().adaptTo(Session.class);
    if (session == null) {
      throw new IllegalStateException("Sparse Session not found");
    }

    // check that the submitted parameter values have valid values.
    String principalName = request.getParameter(SlingPostConstants.RP_NODE_NAME);
    if (principalName == null) {
      throw new IllegalStateException("User name was not submitted");
    }

    NameSanitizer san = new NameSanitizer(principalName, true);
    san.validate();

    String pwd = request.getParameter("pwd");
    if (pwd == null) {
      throw new RepositoryException("Password was not submitted");
    }
    String pwdConfirm = request.getParameter("pwdConfirm");
    if (!pwd.equals(pwdConfirm)) {
      throw new RepositoryException(
          "Password value does not match the confirmation password");
    }

    Session adminSession = null;
    try {
      adminSession = getAdminSession();
      final AuthorizableManager am = adminSession.getAuthorizableManager();
      final Authorizable existing = am.findAuthorizable(principalName);
      if (existing != null) {
        IllegalStateException e = new IllegalStateException(
            "Cannot create new user it already exists: " + principalName);
        log.warn(e.getMessage());
        response.setStatus(HttpServletResponse.SC_CONFLICT, e.getMessage());
        return;
      }
      final Map<String, Object> empty = Collections.emptyMap();
      if (!am.createUser(principalName, principalName, digestPassword(pwd), empty)) {
        Error e = new Error("Could not create new user");
        log.error(e.getMessage(), e);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        return;
      }
      final Authorizable user = am.findAuthorizable(principalName);
      assert (user != null);

      log.info("User {} created", user.getId());

      final String userPath = user.getId();
      Map<String, RequestProperty> reqProperties = collectContent(request, response,
          userPath);
      response.setPath(userPath);
      response.setLocation(userPath);
      response.setParentLocation(userPath);
      changes.add(Modification.onCreated(userPath));

      // write content from form
      writeContent(adminSession, user, reqProperties, changes);
      final AuthorizableManager authorizableManager = adminSession
          .getAuthorizableManager();
      authorizableManager.updateAuthorizable(user);
      try {
        postProcessorService
            .process(user, adminSession, ModificationType.CREATE, request);
      } catch (RepositoryException e) {
        log.warn("Failed to create user {} ", e.getMessage());
        log.debug("Failed to create user {} ", e.getMessage(), e);
        response.setStatus(HttpServletResponse.SC_CONFLICT, e.getMessage());
        return;
      } catch (Exception e) {
        log.warn(e.getMessage(), e);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        return;
      }

      // Launch an OSGi event for creating a user.
      try {
        Dictionary<String, String> properties = new Hashtable<String, String>();
        properties.put(UserConstants.EVENT_PROP_USERID, principalName);
        EventUtils
            .sendOsgiEvent(properties, UserConstants.TOPIC_USER_CREATED, eventAdmin);
      } catch (Exception e) {
        // Trap all exception so we don't disrupt the normal behaviour.
        log.error("Failed to launch an OSGi event for creating a user.", e);
      }
    } catch (RepositoryException e) {
      log.error(e.getMessage(), e);
      throw e;
    } catch (StorageClientException e) {
      log.error(e.getLocalizedMessage(), e);
      throw new RepositoryException(e);
    } catch (AccessDeniedException e) {
      log.error(e.getLocalizedMessage(), e);
      throw new RepositoryException(e);
    } finally {
      ungetSession(adminSession);
    }
  }

}
