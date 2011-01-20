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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.user.AuthorizablePostProcessService;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class CreateSakaiGroupServletTest {
  private static final String USER_ID = "lance";
  private static final String PASSWORD = "password";
  private static final String GROUP_ID = "g-foo";
  private Session adminSession;
  private AuthorizableManager adminAuthorizableManager;
  private User adminUser;
  private Session userSession;
  private User user;
  private Repository repository;
  private CreateSakaiGroupServlet createSakaiGroupServlet;
  @Mock
  private SlingHttpServletRequest request;
  @Mock
  private ResourceResolver resourceResolver;
  private HtmlResponse response = new HtmlResponse();
  private List<Modification> changes = new ArrayList<Modification>();

  @Before
  public void setup() throws ClientPoolException, StorageClientException,
      AccessDeniedException, ClassNotFoundException {
    BaseMemoryRepository baseMemoryRepository = new BaseMemoryRepository();
    repository = baseMemoryRepository.getRepository();
    assertNotNull(repository);
    adminSession = repository.loginAdministrative();
    assertNotNull(adminSession);
    adminAuthorizableManager = adminSession.getAuthorizableManager();
    assertNotNull(adminAuthorizableManager);

    adminUser = (User) adminAuthorizableManager
        .findAuthorizable(UserConstants.ADMIN_USERID);
    assertNotNull(adminUser);

    assertTrue(adminAuthorizableManager.createUser(USER_ID, "Lance Speelmon", PASSWORD,
        ImmutableMap.of("x", (Object) "y")));
    user = (User) adminAuthorizableManager.findAuthorizable(USER_ID);
    assertNotNull(user);
    userSession = repository.login(USER_ID, PASSWORD);
    assertNotNull(userSession);
    assertEquals(USER_ID, userSession.getUserId());

    when(request.getRemoteUser()).thenReturn(UserConstants.ADMIN_USERID);

    javax.jcr.Session adaptable = mock(javax.jcr.Session.class, Mockito.withSettings()
        .extraInterfaces(SessionAdaptable.class));
    when(((SessionAdaptable) adaptable).getSession()).thenReturn(adminSession);
    when(request.getResourceResolver()).thenReturn(resourceResolver);
    when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(adaptable);

    createSakaiGroupServlet = new CreateSakaiGroupServlet();
    createSakaiGroupServlet.setRepository(repository);
  }

  @After
  public void tearDown() throws ClientPoolException {
    adminSession.logout();
  }

  @Test
  public void testNullGroupName() {
    when(request.getParameter(SlingPostConstants.RP_NODE_NAME)).thenReturn(null);

    try {
      createSakaiGroupServlet.handleOperation(request, response, changes);
      fail();
    } catch (ServletException e) {
      assertEquals("Group name was not submitted", e.getMessage());
    }
  }

  @Test
  public void testNoSession() throws ServletException {
    when(request.getRemoteUser()).thenReturn(UserConstants.ANON_USERID);

    createSakaiGroupServlet.handleOperation(request, response, changes);
    assertEquals(403, response.getStatusCode());
  }

  @Test
  public void testPrincipalExists() throws StorageClientException, AccessDeniedException,
      ServletException {
    when(request.getParameter(SlingPostConstants.RP_NODE_NAME)).thenReturn(GROUP_ID);

    assertTrue(adminAuthorizableManager.createGroup(GROUP_ID, GROUP_ID,
        ImmutableMap.of("x", (Object) "y")));
    final Group group = (Group) adminAuthorizableManager.findAuthorizable(GROUP_ID);
    assertNotNull(group);

    Vector<RequestParameter> parameters = new Vector<RequestParameter>();
    when(request.getParameterNames()).thenReturn(parameters.elements());

    RequestParameterMap requestParameterMap = mock(RequestParameterMap.class);
    when(requestParameterMap.entrySet()).thenReturn(
        new HashSet<Entry<String, RequestParameter[]>>());
    when(request.getRequestParameterMap()).thenReturn(requestParameterMap);

    createSakaiGroupServlet.handleOperation(request, response, changes);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatusCode());
  }

  @Test
  public void testPrincipalNotExists() throws Exception {
    when(resourceResolver.map("/system/userManager/group/g-foo")).thenReturn("");
    when(resourceResolver.map("/system/userManager/group")).thenReturn("");

    when(request.getParameter("foo")).thenReturn("bar");
    Vector<RequestParameter> requestParameters = new Vector<RequestParameter>();
    Vector<String> requestParameterNames = new Vector<String>();
    requestParameterNames.add("foo");
    Map<String, RequestParameter[]> m = new HashMap<String, RequestParameter[]>();
    RequestParameter rp = mock(RequestParameter.class);

    when(rp.getString()).thenReturn("bar");
    when(rp.isFormField()).thenReturn(true);

    RequestParameter[] rpa = { rp };
    requestParameters.add(rp);
    m.put("foo", rpa);
    RequestParameterMap requestParameterMap = mock(RequestParameterMap.class);
    when(requestParameterMap.entrySet()).thenReturn(m.entrySet());

    when(request.getParameter(SlingPostConstants.RP_NODE_NAME)).thenReturn(GROUP_ID);
    when(request.getParameterNames()).thenReturn(requestParameterNames.elements());
    when(request.getRequestParameterMap()).thenReturn(requestParameterMap);
    when(request.getAttribute("javax.servlet.include.context_path")).thenReturn("");
    when(request.getParameter(":displayExtension")).thenReturn("");
    when(request.getResource()).thenReturn(null);
    when(request.getParameterValues(":member@Delete")).thenReturn(new String[] {});
    when(request.getParameterValues(":member")).thenReturn(new String[] {});
    when(request.getParameterValues(":manager@Delete")).thenReturn(new String[] {});
    when(request.getParameterValues(":manager")).thenReturn(new String[] {});
    when(request.getParameterValues(":viewer@Delete")).thenReturn(new String[] {});
    when(request.getParameterValues(":viewer")).thenReturn(new String[] {});

    when(request.getParameterValues("foo")).thenReturn(new String[] { "bar" });

    AuthorizablePostProcessService authorizablePostProcessService = mock(AuthorizablePostProcessService.class);
    createSakaiGroupServlet.postProcessorService = authorizablePostProcessService;

    createSakaiGroupServlet.handleOperation(request, response, changes);

    verify(request, times(2)).getAttribute("javax.servlet.include.context_path");
    verify(request, times(2)).getParameter(":displayExtension");
    verify(authorizablePostProcessService, times(1)).process(any(Authorizable.class),
        any(Session.class), any(ModificationType.class),
        any(SlingHttpServletRequest.class));
    final Group test = (Group) userSession.getAuthorizableManager().findAuthorizable(
        GROUP_ID);
    assertNotNull("Verify a different session can find the group", test);
    assertEquals("bar", StorageClientUtils.toString(test.getSafeProperties().get("foo")));
  }
}
