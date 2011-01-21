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
package org.sakaiproject.nakamura.calendar.signup;

import static org.apache.sling.jcr.resource.JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sakaiproject.nakamura.api.calendar.CalendarConstants.PARTICIPANTS_NODE_NAME;
import static org.sakaiproject.nakamura.api.calendar.CalendarConstants.SAKAI_EVENT_SIGNUP_PARTICIPANT_RT;

import com.google.common.collect.ImmutableMap;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sakaiproject.nakamura.api.calendar.CalendarException;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.lite.RepositoryImpl;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

/**
 *
 */
public class CalendarSignupServletTest {

  private CalendarSignupServlet servlet;
  private String signupPath;
  private String userName;
  private BaseMemoryRepository baseMemoryRepository;
  private RepositoryImpl sparseRepository;
  private Session session;

  public CalendarSignupServletTest() throws ClientPoolException, StorageClientException, AccessDeniedException, ClassNotFoundException {
    baseMemoryRepository = new BaseMemoryRepository();
    sparseRepository = baseMemoryRepository.getRepository();
    session = sparseRepository.loginAdministrative();
    session.getAuthorizableManager().createUser("ieb", "Ian Boston", "test",
        ImmutableMap.of("x", StorageClientUtils.toStore("y")));
    session.getContentManager().update(
        new Content("a:ieb", null));
    session.getAccessControlManager().setAcl(
        Security.ZONE_CONTENT,
        "a:ieb",
        new AclModification[] { new AclModification(AclModification.grantKey("ieb"),
            Permissions.CAN_MANAGE.getPermission(), Operation.OP_REPLACE) });
    signupPath = "a:ieb/path/to/calendar/path/to/event/signup";
    session.getContentManager().update(new Content(signupPath,null));
  
    session.logout();
    session = sparseRepository.loginAdministrative("ieb");

  }

  @Before
  public void setUp() throws Exception {
    servlet = new CalendarSignupServlet();
    servlet.sparseRepository = sparseRepository;


  }

  @Test
  public void testAnon() throws ServletException, IOException {
    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
    when(request.getRemoteUser()).thenReturn(UserConstants.ANON_USERID);
    servlet.doPost(request, response);

    verify(response).sendError(Mockito.eq(HttpServletResponse.SC_UNAUTHORIZED),
        Mockito.anyString());
  }

  @Test
  public void testSignedupAlready() throws Exception {

    
    Session session = sparseRepository.loginAdministrative("ieb");
    ContentManager contentManager = session.getContentManager();
    
    String path = signupPath + "/" + PARTICIPANTS_NODE_NAME+ "/ieb";
    
    contentManager.update(new Content(path, null));
    Content signupNode = contentManager.get(signupPath);

    try {
      servlet.checkAlreadySignedup(signupNode, session);
      fail("This should have thrown an exception.");
    } catch (CalendarException e) {
      assertEquals(400, e.getCode());
    }
  }

  @Test
  public void testHandleSignup() throws Exception {

    // Participant node.
    String path = signupPath + "/" + PARTICIPANTS_NODE_NAME+ "/ieb";
    
    Session session = sparseRepository.loginAdministrative("ieb");
    ContentManager contentManager = session.getContentManager();
    
    contentManager.delete(path);
    Content signupNode = contentManager.get(signupPath);


    servlet.handleSignup(signupNode,session);
    
    Content participantsNode = contentManager.get(path);

    assertEquals(StorageClientUtils.toString(participantsNode.getProperty(SLING_RESOURCE_TYPE_PROPERTY)),
        SAKAI_EVENT_SIGNUP_PARTICIPANT_RT);
    assertEquals(StorageClientUtils.toString(participantsNode.getProperty("sakai:user")), "ieb");
  }

}
