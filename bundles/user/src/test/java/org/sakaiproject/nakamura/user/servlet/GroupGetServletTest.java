package org.sakaiproject.nakamura.user.servlet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class GroupGetServletTest {
  private static final String PASSWORD = "password";
  private static final String USER_ID = "lance";
  private static final String GROUP_ID = "g-foo";
  @Mock
  private SlingHttpServletRequest request;
  @Mock
  private SlingHttpServletResponse response;
  @Mock
  private ResourceResolver resourceResolver;
  private Group group;
  private Session session;
  private Session adminSession;
  private AuthorizableManager authorizableManager;
  private AuthorizableManager adminAuthorizableManager;
  private Repository repository;
  private User user;
  private GroupGetServlet ggs = null;
  @Mock
  private PrintWriter write;

  @Before
  public void setUp() throws Exception {
    BaseMemoryRepository baseMemoryRepository = new BaseMemoryRepository();
    repository = baseMemoryRepository.getRepository();
    assertNotNull(repository);
    adminSession = repository.loginAdministrative();
    assertNotNull(adminSession);
    adminAuthorizableManager = adminSession.getAuthorizableManager();
    assertNotNull(adminAuthorizableManager);
    assertTrue(adminAuthorizableManager.createGroup(GROUP_ID, "Group Name",
        ImmutableMap.of("foo", (Object) "bar")));
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
    // Needs to be more verbose, alas.
    javax.jcr.Session adaptable = mock(javax.jcr.Session.class, Mockito.withSettings()
        .extraInterfaces(SessionAdaptable.class));
    when(((SessionAdaptable) adaptable).getSession()).thenReturn(session);
    when(request.getResourceResolver()).thenReturn(resourceResolver);
    when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(adaptable);
    when(resourceResolver.getUserID()).thenReturn(USER_ID);
    when(request.getRemoteUser()).thenReturn(USER_ID);
    when(response.getWriter()).thenReturn(write);
    ggs = new GroupGetServlet();
  }

  @Test
  public void testNullAuthorizable() throws Exception {
    badAuthorizable(null);
  }

  @Test
  public void testNonGroupAuthorizable() throws Exception {
    badAuthorizable(user);
  }

  private void badAuthorizable(Authorizable authorizable) throws IOException,
      ServletException {
    final Resource resource = mock(Resource.class);
    when(resource.adaptTo(Authorizable.class)).thenReturn(authorizable);
    when(request.getResource()).thenReturn(resource);

    ggs.doGet(request, response);
    verify(response, times(1)).sendError(eq(HttpServletResponse.SC_NO_CONTENT),
        anyString());
  }

  @Test
  public void testNoSession() throws Exception {
    final Resource resource = mock(Resource.class);
    when(resource.adaptTo(Authorizable.class)).thenReturn(group);
    // null Session
    when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(null);
    when(request.getResource()).thenReturn(resource);

    ggs.doGet(request, response);
    verify(response, times(1)).sendError(
        eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR), anyString());
  }

  @Test
  public void testGoodRequest() throws Exception {
    final Resource resource = mock(Resource.class);
    when(resource.adaptTo(Authorizable.class)).thenReturn(group);
    when(request.getResource()).thenReturn(resource);

    ggs.doGet(request, response);
    verify(response).setContentType("application/json");
    verify(response).setCharacterEncoding("UTF-8");
    verify(write, atLeastOnce()).write(any(String.class));
  }

  @Test
  public void testRepositoryExceptionHandling() throws Exception {
    Group authorizable = mock(Group.class);
    when(authorizable.getId()).thenReturn(GROUP_ID);
    when(authorizable.getMembers()).thenThrow(new NullPointerException());
    Resource resource = mock(Resource.class);
    when(resource.adaptTo(Authorizable.class)).thenReturn(authorizable);
    when(request.getResource()).thenReturn(resource);

    ggs.doGet(request, response);
    verify(response).setContentType("application/json");
    verify(response).setCharacterEncoding("UTF-8");
    verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
        anyString());
    verify(write, atLeastOnce()).write(any(String.class));
  }
}
