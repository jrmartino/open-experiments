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
package org.sakaiproject.nakamura.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.user.AuthorizablePostProcessor;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.lite.RepositoryImpl;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizablePostProcessorServiceTest {
  private static final String GROUP_ID = "faculty";
  private static final String USER_ID = "joe";
  private AuthorizablePostProcessServiceImpl authorizablePostProcessService;
  private Session session;
  private User user;
  private Group group;
  @Mock
  SlingHttpServletRequest request;
  @Mock
  AuthorizablePostProcessor authorizablePostProcessor;
  @Mock
  SakaiUserProcessor sakaiUserProcessor;
  @Mock
  SakaiGroupProcessor sakaiGroupProcessor;
  private RepositoryImpl repository;

  @BeforeClass
  public void setUpClass() throws ClientPoolException, StorageClientException, AccessDeniedException, ClassNotFoundException {
    BaseMemoryRepository baseMemoryRepository = new BaseMemoryRepository();
    repository = baseMemoryRepository.getRepository();
    session = repository.loginAdministrative();
    AuthorizableManager authorizableManager = session.getAuthorizableManager();
    authorizableManager.createUser(USER_ID, "Lance Speelmon", "password",
        ImmutableMap.of("x", (Object) "y"));
    user = (User) authorizableManager.findAuthorizable(USER_ID);
    authorizableManager.createGroup(GROUP_ID, "Faculty", ImmutableMap.of("x", (Object) "y"));
    group = (Group) authorizableManager.findAuthorizable(GROUP_ID);
  }
  
  @AfterClass
  public void tearDown() throws ClientPoolException {
    session.logout();
  }

  @Before
  public void setUp() throws ClientPoolException, StorageClientException, AccessDeniedException, ClassNotFoundException {
    authorizablePostProcessService = new AuthorizablePostProcessServiceImpl();
    authorizablePostProcessService.sakaiUserProcessor = sakaiUserProcessor;
    authorizablePostProcessService.sakaiGroupProcessor = sakaiGroupProcessor;
    authorizablePostProcessService.bindAuthorizablePostProcessor(authorizablePostProcessor, new HashMap<String, Object>());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void extractsNonpersistedRequestParameters() throws Exception {
    List<String> parameters = Arrays.asList(new String [] {"donotpass", ":dopass"});
    when(request.getParameterNames()).thenReturn(Collections.enumeration(parameters));

    RequestParameter requestParameter = mock(RequestParameter.class, RETURNS_DEEP_STUBS);
    RequestParameterMap requestParameterMap = mock(RequestParameterMap.class);
    when(requestParameterMap.keySet()).thenReturn(new HashSet<String>(parameters));
    when(requestParameterMap.getValues(anyString())).thenReturn(new RequestParameter[] {requestParameter});
    when(request.getRequestParameterMap()).thenReturn(requestParameterMap);

    @SuppressWarnings("rawtypes")
    ArgumentCaptor<Map> mapArgument = ArgumentCaptor.forClass(Map.class);
    authorizablePostProcessService.process(user, session, ModificationType.MODIFY, request);
    verify(authorizablePostProcessor).process(eq(user), eq(session), any(Modification.class), mapArgument.capture());
    assertTrue(mapArgument.getValue().size() == 1);
    assertNotNull(mapArgument.getValue().get(":dopass"));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void parametersAreOptional() throws Exception {
    @SuppressWarnings("rawtypes")
    ArgumentCaptor<Map> mapArgument = ArgumentCaptor.forClass(Map.class);
    authorizablePostProcessService.process(user, session, ModificationType.MODIFY);
    verify(authorizablePostProcessor).process(eq(user), eq(session), any(Modification.class), mapArgument.capture());
    assertTrue(mapArgument.getValue().size() == 0);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void userProcessingOccursBeforeOtherNondeleteProcessing() throws Exception {
    authorizablePostProcessService.process(user, session, ModificationType.MODIFY);
    InOrder inOrder = inOrder(sakaiUserProcessor, authorizablePostProcessor);
    inOrder.verify(sakaiUserProcessor).process(eq(user), eq(session), any(Modification.class), any(Map.class));
    verify(sakaiGroupProcessor, never()).process(eq(user), eq(session), any(Modification.class), any(Map.class));
    inOrder.verify(authorizablePostProcessor).process(eq(user), eq(session), any(Modification.class), any(Map.class));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void userProcessingOccursAfterOtherDeleteProcessing() throws Exception {
    authorizablePostProcessService.process(user, session, ModificationType.DELETE);
    InOrder inOrder = inOrder(authorizablePostProcessor, sakaiUserProcessor);
    inOrder.verify(authorizablePostProcessor).process(eq(user), eq(session), any(Modification.class), any(Map.class));
    inOrder.verify(sakaiUserProcessor).process(eq(user), eq(session), any(Modification.class), any(Map.class));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void groupProcessingOccurs() throws Exception {
    authorizablePostProcessService.process(group, session, ModificationType.MODIFY);
    verify(sakaiUserProcessor, never()).process(eq(group), eq(session), any(Modification.class), any(Map.class));
    verify(sakaiGroupProcessor).process(eq(group), eq(session), any(Modification.class), any(Map.class));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void modificationArgumentContainsUserPath() throws Exception {
    ArgumentCaptor<Modification> modificationArgument = ArgumentCaptor.forClass(Modification.class);
    authorizablePostProcessService.process(user, session, ModificationType.CREATE);
    verify(authorizablePostProcessor).process(eq(user), eq(session), modificationArgument.capture(), any(Map.class));
    Modification modification = modificationArgument.getValue();
    assertEquals(ModificationType.CREATE, modification.getType());
    assertEquals(UserConstants.SYSTEM_USER_MANAGER_USER_PREFIX + USER_ID, modification.getSource());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void modificationArgumentContainsGroupPath() throws Exception {
    ArgumentCaptor<Modification> modificationArgument = ArgumentCaptor.forClass(Modification.class);
    authorizablePostProcessService.process(group, session, ModificationType.CREATE);
    verify(authorizablePostProcessor).process(eq(group), eq(session), modificationArgument.capture(), any(Map.class));
    Modification modification = modificationArgument.getValue();
    assertEquals(ModificationType.CREATE, modification.getType());
    assertEquals(UserConstants.SYSTEM_USER_MANAGER_GROUP_PREFIX + GROUP_ID, modification.getSource());
  }
}
