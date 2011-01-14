package org.sakaiproject.nakamura.user.servlet;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.api.auth.trusted.RequestTrustValidator;
import org.sakaiproject.nakamura.api.auth.trusted.RequestTrustValidatorService;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.testutils.easymock.AbstractEasyMockTest;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

public class CreateSakaiUserServletTest extends AbstractEasyMockTest {

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

    ResourceResolver rr = createMock(ResourceResolver.class);

    SlingHttpServletRequest request = createMock(SlingHttpServletRequest.class);
    expect(request.getResourceResolver()).andReturn(rr).anyTimes();
    expect(rr.adaptTo(Session.class)).andReturn(session).anyTimes();

    expect(request.getParameter(":create-auth")).andReturn("reCAPTCHA");
    expect(request.getParameter(SlingPostConstants.RP_NODE_NAME)).andReturn(name);

    HtmlResponse response = new HtmlResponse();

    replay();

    try {
      csus.handleOperation(request, response, null);
      fail();
    } catch (ServletException e) {
      assertEquals(exception, e.getMessage());
    }
    verify();
  }

  @Test
  public void testNoPwd() throws ServletException, StorageClientException,
      AccessDeniedException {
    CreateSakaiUserServlet csus = new CreateSakaiUserServlet();
    csus.requestTrustValidatorService = requestTrustValidatorService;

    Session session = repository.loginAdministrative(USER_ID);

    ResourceResolver rr = createMock(ResourceResolver.class);
    expect(rr.adaptTo(Session.class)).andReturn(session);

    SlingHttpServletRequest request = createMock(SlingHttpServletRequest.class);
    expect(request.getResourceResolver()).andReturn(rr).anyTimes();
    expect(rr.adaptTo(Session.class)).andReturn(session).anyTimes();

    expect(request.getParameter(":create-auth")).andReturn("reCAPTCHA");

    expect(request.getParameter(SlingPostConstants.RP_NODE_NAME)).andReturn("foo");
    expect(request.getParameter("pwd")).andReturn(null);

    HtmlResponse response = new HtmlResponse();

    replay();

    try {
      csus.handleOperation(request, response, null);
      fail();
    } catch (ServletException e) {
      assertEquals("Password was not submitted", e.getMessage());
    }
    verify();
  }

  @Test
  public void testNotPwdEqualsPwdConfirm() throws ServletException,
      StorageClientException, AccessDeniedException {
    CreateSakaiUserServlet csus = new CreateSakaiUserServlet();
    csus.requestTrustValidatorService = requestTrustValidatorService;

    Session session = repository.loginAdministrative(USER_ID);

    ResourceResolver rr = createMock(ResourceResolver.class);
    expect(rr.adaptTo(Session.class)).andReturn(session);

    SlingHttpServletRequest request = createMock(SlingHttpServletRequest.class);
    expect(request.getResourceResolver()).andReturn(rr).anyTimes();
    expect(rr.adaptTo(Session.class)).andReturn(session).anyTimes();

    expect(request.getParameter(":create-auth")).andReturn("reCAPTCHA");

    expect(request.getParameter(SlingPostConstants.RP_NODE_NAME)).andReturn("foo");
    expect(request.getParameter("pwd")).andReturn("bar");
    expect(request.getParameter("pwdConfirm")).andReturn("baz");

    HtmlResponse response = new HtmlResponse();

    replay();

    try {
      csus.handleOperation(request, response, null);
      fail();
    } catch (ServletException e) {
      assertEquals("Password value does not match the confirmation password",
          e.getMessage());
    }
    verify();
  }

  @Test
  public void testRequestTrusted() throws ServletException, StorageClientException,
      AccessDeniedException {
    CreateSakaiUserServlet csus = new CreateSakaiUserServlet();

    Session session = repository.loginAdministrative(USER_ID);

    ResourceResolver rr = createMock(ResourceResolver.class);
    expect(rr.adaptTo(Session.class)).andReturn(session);

    SlingHttpServletRequest request = createMock(SlingHttpServletRequest.class);
    expect(request.getResourceResolver()).andReturn(rr).anyTimes();
    expect(rr.adaptTo(Session.class)).andReturn(session).anyTimes();

    expect(request.getParameter(":create-auth")).andReturn("typeA");
    RequestTrustValidatorService requestTrustValidatorService = createMock(RequestTrustValidatorService.class);
    RequestTrustValidator requestTrustValidator = createMock(RequestTrustValidator.class);
    expect(requestTrustValidatorService.getValidator("typeA")).andReturn(
        requestTrustValidator);
    expect(requestTrustValidator.getLevel()).andReturn(RequestTrustValidator.CREATE_USER);
    expect(requestTrustValidator.isTrusted(request)).andReturn(true);

    expect(request.getParameter(SlingPostConstants.RP_NODE_NAME)).andReturn("foo");
    expect(request.getParameter("pwd")).andReturn("bar");
    expect(request.getParameter("pwdConfirm")).andReturn("baz");

    HtmlResponse response = new HtmlResponse();

    csus.requestTrustValidatorService = requestTrustValidatorService;
    replay();

    try {
      csus.handleOperation(request, response, null);
      fail();
    } catch (ServletException e) {
      assertEquals("Password value does not match the confirmation password",
          e.getMessage());
    }
    verify();
  }
}
