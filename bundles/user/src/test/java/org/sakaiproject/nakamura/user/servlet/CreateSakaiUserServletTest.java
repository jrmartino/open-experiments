/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.user.servlet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sakaiproject.nakamura.api.auth.trusted.RequestTrustValidator;
import org.sakaiproject.nakamura.api.auth.trusted.RequestTrustValidatorService;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

public class CreateSakaiUserServletTest {

  private static final String USER_ID = "userID";
  private RequestTrustValidatorService requestTrustValidatorService;
  private Repository repository;

  @Before
  public void setUp() throws Exception {
    requestTrustValidatorService = new RequestTrustValidatorService() {

      public RequestTrustValidator getValidator(String name) {
        return new RequestTrustValidator() {

          public boolean isTrusted(HttpServletRequest request) {
            return true;
          }

          public int getLevel() {
            return RequestTrustValidator.CREATE_USER;
          }
        };
      }
    };
    BaseMemoryRepository baseMemoryRepository = new BaseMemoryRepository();
    repository = baseMemoryRepository.getRepository();
    Session session = repository.loginAdministrative();
    AuthorizableManager authorizableManager = session.getAuthorizableManager();
    authorizableManager.createUser(USER_ID, "Lance Speelmon", "password",
        ImmutableMap.of("x", (Object) "y"));
    session.logout();
  }

  @Test
  public void testNoPrincipalName() throws ServletException, StorageClientException,
      AccessDeniedException {
    badNodeNameParam(null, "User name was not submitted");
  }

  @Test
  public void testBadPrefix() throws ServletException, StorageClientException,
      AccessDeniedException {
    badNodeNameParam("g-contacts-all", "'g-contacts-' is a reserved prefix.");
  }

  private void badNodeNameParam(String name, String exception) throws ServletException,
      StorageClientException, AccessDeniedException {
    CreateSakaiUserServlet csus = new CreateSakaiUserServlet();
    csus.requestTrustValidatorService = requestTrustValidatorService;

    Session session = repository.loginAdministrative(USER_ID);

    ResourceResolver rr = mock(ResourceResolver.class);

    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    when(request.getResourceResolver()).thenReturn(rr);
    javax.jcr.Session adaptable = mock(javax.jcr.Session.class, Mockito.withSettings()
        .extraInterfaces(SessionAdaptable.class));
    when(((SessionAdaptable) adaptable).getSession()).thenReturn(session);
    when(rr.adaptTo(javax.jcr.Session.class)).thenReturn(adaptable);

    when(request.getParameter(":create-auth")).thenReturn("reCAPTCHA");
    when(request.getParameter(SlingPostConstants.RP_NODE_NAME)).thenReturn(name);

    HtmlResponse response = new HtmlResponse();

    try {
      csus.handleOperation(request, response, null);
      fail();
    } catch (ServletException e) {
      assertEquals(exception, e.getMessage());
    }
  }

  @Test
  public void testNoPwd() throws ServletException, StorageClientException,
      AccessDeniedException {
    CreateSakaiUserServlet csus = new CreateSakaiUserServlet();
    csus.requestTrustValidatorService = requestTrustValidatorService;

    Session session = repository.loginAdministrative(USER_ID);

    ResourceResolver rr = mock(ResourceResolver.class);
    when(rr.adaptTo(Session.class)).thenReturn(session);

    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    when(request.getResourceResolver()).thenReturn(rr);
    javax.jcr.Session adaptable = mock(javax.jcr.Session.class, Mockito.withSettings()
        .extraInterfaces(SessionAdaptable.class));
    when(((SessionAdaptable) adaptable).getSession()).thenReturn(session);
    when(rr.adaptTo(javax.jcr.Session.class)).thenReturn(adaptable);

    when(request.getParameter(":create-auth")).thenReturn("reCAPTCHA");

    when(request.getParameter(SlingPostConstants.RP_NODE_NAME)).thenReturn("foo");
    when(request.getParameter("pwd")).thenReturn(null);

    HtmlResponse response = new HtmlResponse();

    try {
      csus.handleOperation(request, response, null);
      fail();
    } catch (ServletException e) {
      assertEquals("Password was not submitted", e.getMessage());
    }
  }

  @Test
  public void testNotPwdEqualsPwdConfirm() throws ServletException,
      StorageClientException, AccessDeniedException {
    CreateSakaiUserServlet csus = new CreateSakaiUserServlet();
    csus.requestTrustValidatorService = requestTrustValidatorService;

    Session session = repository.loginAdministrative(USER_ID);

    ResourceResolver rr = mock(ResourceResolver.class);
    javax.jcr.Session adaptable = mock(javax.jcr.Session.class, Mockito.withSettings()
        .extraInterfaces(SessionAdaptable.class));
    when(((SessionAdaptable) adaptable).getSession()).thenReturn(session);
    when(rr.adaptTo(javax.jcr.Session.class)).thenReturn(adaptable);

    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    when(request.getResourceResolver()).thenReturn(rr);
    when(rr.adaptTo(Session.class)).thenReturn(session);

    when(request.getParameter(":create-auth")).thenReturn("reCAPTCHA");

    when(request.getParameter(SlingPostConstants.RP_NODE_NAME)).thenReturn("foo");
    when(request.getParameter("pwd")).thenReturn("bar");
    when(request.getParameter("pwdConfirm")).thenReturn("baz");

    HtmlResponse response = new HtmlResponse();

    try {
      csus.handleOperation(request, response, null);
      fail();
    } catch (ServletException e) {
      assertEquals("Password value does not match the confirmation password",
          e.getMessage());
    }
  }

  @Test
  public void testRequestTrusted() throws ServletException, StorageClientException,
      AccessDeniedException {
    CreateSakaiUserServlet csus = new CreateSakaiUserServlet();

    Session session = repository.loginAdministrative(USER_ID);

    ResourceResolver rr = mock(ResourceResolver.class);
    when(rr.adaptTo(Session.class)).thenReturn(session);

    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    when(request.getResourceResolver()).thenReturn(rr);
    javax.jcr.Session adaptable = mock(javax.jcr.Session.class, Mockito.withSettings()
        .extraInterfaces(SessionAdaptable.class));
    when(((SessionAdaptable) adaptable).getSession()).thenReturn(session);
    when(rr.adaptTo(javax.jcr.Session.class)).thenReturn(adaptable);

    when(request.getParameter(":create-auth")).thenReturn("typeA");
    RequestTrustValidatorService requestTrustValidatorService = mock(RequestTrustValidatorService.class);
    RequestTrustValidator requestTrustValidator = mock(RequestTrustValidator.class);
    when(requestTrustValidatorService.getValidator("typeA")).thenReturn(
        requestTrustValidator);
    when(requestTrustValidator.getLevel()).thenReturn(RequestTrustValidator.CREATE_USER);
    when(requestTrustValidator.isTrusted(request)).thenReturn(true);

    when(request.getParameter(SlingPostConstants.RP_NODE_NAME)).thenReturn("foo");
    when(request.getParameter("pwd")).thenReturn("bar");
    when(request.getParameter("pwdConfirm")).thenReturn("baz");

    HtmlResponse response = new HtmlResponse();

    csus.requestTrustValidatorService = requestTrustValidatorService;

    try {
      csus.handleOperation(request, response, null);
      fail();
    } catch (ServletException e) {
      assertEquals("Password value does not match the confirmation password",
          e.getMessage());
    }
  }
}
