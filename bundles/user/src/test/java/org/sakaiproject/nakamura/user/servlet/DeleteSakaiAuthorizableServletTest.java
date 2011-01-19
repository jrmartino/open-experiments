package org.sakaiproject.nakamura.user.servlet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceNotFoundException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.user.AuthorizablePostProcessService;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

public class DeleteSakaiAuthorizableServletTest {
  @Mock
  Session session;
  @Mock
  SlingHttpServletRequest request;
  @Mock
  ResourceResolver resourceResolver;
  @Mock
  AuthorizablePostProcessService authorizablePostProcessService;

  // Can't mock with annotations because we need to mix in the SessionAdaptable interface.
  javax.jcr.Session adaptableJcrSession;

  List<Modification> changes;
  HtmlResponse response;

  public DeleteSakaiAuthorizableServletTest() {
    MockitoAnnotations.initMocks(this);
  }

  @Before
  public void setUp() throws Exception {
    javax.jcr.Session adaptable = mock(javax.jcr.Session.class, Mockito.withSettings().extraInterfaces(SessionAdaptable.class));
    when(((SessionAdaptable)adaptable).getSession()).thenReturn(session);
    when(request.getResourceResolver()).thenReturn(resourceResolver);
    when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(adaptable);
    changes = new ArrayList<Modification>();
    response = new HtmlResponse();
  }

  @Test
  public void testApplyToAllowsNullResources() {
    // This test checks that processing of a list of ":applyTo" IDs will continue
    // without throwing an exception, even if the list is empty.
    when(request.getParameterValues(":applyTo")).thenReturn(new String[] {});
    when(request.getResource()).thenReturn(null);

    DeleteSakaiAuthorizableServlet dsas = new DeleteSakaiAuthorizableServlet();
    dsas.postProcessorService = authorizablePostProcessService;
    dsas.handleOperation(request, response, changes);

    // Nothing happened (since we didn't get an exception). Did an unwanted error get
    // reported?
    assertFalse(response.getStatusCode() == HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  public void testErrorForNullResource() {
    when(request.getParameterValues(":applyTo")).thenReturn(null);
    when(request.getResource()).thenReturn(null);

    DeleteSakaiAuthorizableServlet dsas = new DeleteSakaiAuthorizableServlet();
    dsas.postProcessorService = authorizablePostProcessService;
    try {
      dsas.handleOperation(request, response, changes);
      fail("Null resource should result in ResourceNotFoundException");
    } catch (ResourceNotFoundException e) {
    }
    assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatusCode());
  }

  @Test
  public void testErrorForUnfoundResource() {
    Resource resource = mock(Resource.class);
    when(resource.adaptTo(Authorizable.class)).thenReturn(null);
    when(request.getParameterValues(":applyTo")).thenReturn(null);
    when(request.getResource()).thenReturn(resource);

    DeleteSakaiAuthorizableServlet dsas = new DeleteSakaiAuthorizableServlet();
    dsas.postProcessorService = authorizablePostProcessService;
    try {
      dsas.handleOperation(request, response, changes);
      fail("Null resource should result in ResourceNotFoundException");
    } catch (ResourceNotFoundException e) {
    }
    assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatusCode());
  }
}
