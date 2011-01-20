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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
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
import java.util.List;
import java.util.Vector;

@RunWith(MockitoJUnitRunner.class)
public class UpdateSakaiGroupServletTest {
  private static final String USER_ID = "lance";
  private static final String PASSWORD = "password";
  private static final String GROUP_ID = "g-foo";
  private Session adminSession;
  private AuthorizableManager adminAuthorizableManager;
  private User adminUser;
  private Session userSession;
  private User user;
  private Repository repository;
  private UpdateSakaiGroupServlet updateSakaiGroupServlet;
  @Mock
  private SlingHttpServletRequest request;
  @Mock
  private ResourceResolver resourceResolver;
  private List<Modification> changes = new ArrayList<Modification>();
  private Group group;
  @Mock
  AuthorizablePostProcessService authorizablePostProcessService;

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

    assertTrue(adminAuthorizableManager.createGroup(GROUP_ID, "Group Name",
        ImmutableMap.of("x", (Object) "y")));
    group = (Group) adminAuthorizableManager.findAuthorizable(GROUP_ID);
    assertNotNull(group);

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

    updateSakaiGroupServlet = new UpdateSakaiGroupServlet();
    updateSakaiGroupServlet.bindRepository(repository);
  }

  @Test
  public void testHandleOperation() throws Exception {
    Resource resource = mock(Resource.class);
    when(resource.adaptTo(Authorizable.class)).thenReturn(group);

    Vector<String> params = new Vector<String>();
    HashMap<String, RequestParameter[]> rpm = new HashMap<String, RequestParameter[]>();

    RequestParameterMap requestParameterMap = mock(RequestParameterMap.class);
    when(requestParameterMap.entrySet()).thenReturn(rpm.entrySet());

    when(request.getResource()).thenReturn(resource);
    when(request.getParameterNames()).thenReturn(params.elements());
    when(request.getRequestParameterMap()).thenReturn(requestParameterMap);
    when(request.getParameterValues(":member@Delete")).thenReturn(new String[] {});
    when(request.getParameterValues(":member")).thenReturn(new String[] {});
    when(request.getParameterValues(":manager@Delete")).thenReturn(new String[] {});
    when(request.getParameterValues(":manager")).thenReturn(new String[] {});
    when(request.getParameterValues(":viewer@Delete")).thenReturn(new String[] {});
    when(request.getParameterValues(":viewer")).thenReturn(new String[] {});

    HtmlResponse response = mock(HtmlResponse.class);

    updateSakaiGroupServlet.postProcessorService = authorizablePostProcessService;
    updateSakaiGroupServlet.handleOperation(request, response, changes);

    verify(request, times(2)).getResource();
    verify(request, times(3)).getResourceResolver();
    verify(authorizablePostProcessService).process(any(Authorizable.class),
        any(Session.class), eq(ModificationType.MODIFY),
        any(SlingHttpServletRequest.class));
  }
}
