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
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;

public class AbstractSakaiGroupPostServletTest {

  private static final String PASSWORD = "password";
  private static final String USER_ID = "lance";
  private static final String GROUP_ID = "groupId";
  @Mock
  private SlingHttpServletRequest request;
  @Mock
  private ResourceResolver resourceResolver;
  private Group group;
  private Session session;
  private Session adminSession;
  private AuthorizableManager authorizableManager;
  private AuthorizableManager adminAuthorizableManager;
  private Repository repository;
  private User user;
  private AbstractSakaiGroupPostServlet servlet;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    BaseMemoryRepository baseMemoryRepository = new BaseMemoryRepository();
    repository = baseMemoryRepository.getRepository();
    assertNotNull(repository);
    adminSession = repository.loginAdministrative();
    assertNotNull(adminSession);
    adminAuthorizableManager = adminSession.getAuthorizableManager();
    assertNotNull(adminAuthorizableManager);
    // By default the user 'jeff' is always a manager of the group.
    assertTrue(adminAuthorizableManager.createGroup(GROUP_ID, "Group Name",
        ImmutableMap.of(UserConstants.PROP_GROUP_MANAGERS, (Object) "jeff")));
    group = (Group) adminAuthorizableManager.findAuthorizable(GROUP_ID);
    assertNotNull(group);

    assertTrue(adminAuthorizableManager.createUser(USER_ID, "Lance Speelmon", PASSWORD,
        ImmutableMap.of("x", (Object) "y")));
    user = (User) adminAuthorizableManager.findAuthorizable(USER_ID);
    assertNotNull(user);
    session = repository.login(USER_ID, PASSWORD);
    assertNotNull(session);
    assertEquals(USER_ID, session.getUserId());
    authorizableManager = session.getAuthorizableManager();
    assertNotNull(authorizableManager);
    when(request.getResourceResolver()).thenReturn(resourceResolver);
    when(resourceResolver.adaptTo(Session.class)).thenReturn(session);
    when(resourceResolver.getUserID()).thenReturn(USER_ID);
    when(request.getRemoteUser()).thenReturn(USER_ID);

    servlet = new AbstractSakaiGroupPostServlet() {

      private static final long serialVersionUID = -475691532657996299L;

      @Override
      protected void handleOperation(SlingHttpServletRequest request,
          HtmlResponse htmlResponse, List<Modification> changes) throws ServletException {
        // we will not be testing this method in this class but it is abstract
      }
    };
    servlet.repository = repository;
  }

  @After
  public void teardown() throws ClientPoolException {
    adminSession.logout();
    session.logout();
  }

  @Test
  public void testAddManager() throws Exception {
    when(request.getParameterValues(":manager")).thenReturn(
        new String[] { "jack", "john" });
    when(request.getParameterValues(":manager@Delete")).thenReturn(null);

    servlet.updateOwnership(request, group, new String[] { "joe" }, null);

    // re-fetch group to be cautious
    group = (Group) authorizableManager.findAuthorizable(GROUP_ID);

    List<Object> values = Arrays.asList((Object[]) group
        .getProperty(UserConstants.PROP_GROUP_MANAGERS));

    assertTrue(values.contains("jeff"));
    assertTrue(values.contains("jack"));
    assertTrue(values.contains("john"));
    assertTrue(values.contains("joe"));
    assertEquals(4, values.size());
  }

  @Test
  public void testDeleteManager() throws Exception {
    // Remove jeff, add jack
    when(request.getParameterValues(":manager")).thenReturn(new String[] { "jack" });
    when(request.getParameterValues(":manager@Delete")).thenReturn(
        new String[] { "jeff" });

    servlet.updateOwnership(request, group, new String[0], null);

    // re-fetch group to be cautious
    group = (Group) authorizableManager.findAuthorizable(GROUP_ID);

    List<Object> values = Arrays.asList((Object[]) group
        .getProperty(UserConstants.PROP_GROUP_MANAGERS));
    assertTrue(values.contains("jack"));
    assertEquals(1, values.size());
  }

  @Test
  public void testNonJoinableGroup() throws Exception {
    when(request.getParameterValues(":member")).thenReturn(new String[] { USER_ID });
    assertTrue(adminAuthorizableManager.createGroup("nonJoinable", "Non-Joinable",
        ImmutableMap.of(UserConstants.PROP_JOINABLE_GROUP, (Object) "no")));
    group = (Group) authorizableManager.findAuthorizable("nonJoinable");
    assertNotNull(group);

    ArrayList<Modification> changes = new ArrayList<Modification>();
    try {
      servlet.updateGroupMembership(request, group, changes);
      fail();
    } catch (AccessDeniedException e) {
      assertNotNull(e);
    }
    assertTrue(changes.size() == 0);
  }

  @Test
  public void testJoinableGroup() throws Exception {
    when(request.getParameterValues(":member")).thenReturn(new String[] { USER_ID });
    assertTrue(adminAuthorizableManager.createGroup("g-foo", "Joinable",
        ImmutableMap.of(UserConstants.PROP_JOINABLE_GROUP, (Object) "yes")));
    Group joinable = (Group) authorizableManager.findAuthorizable("g-foo");
    assertNotNull(joinable);

    adminSession.logout();

    ArrayList<Modification> changes = new ArrayList<Modification>();

    servlet.updateGroupMembership(request, joinable, changes);

    joinable = (Group) authorizableManager.findAuthorizable("g-foo");

    List<String> members = Arrays.asList(joinable.getMembers());
    assertTrue(members.contains(USER_ID));
    assertTrue(changes.size() > 0);
  }

}
