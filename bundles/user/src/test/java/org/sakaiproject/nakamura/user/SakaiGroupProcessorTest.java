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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sakaiproject.nakamura.api.lite.StorageClientUtils.toStore;
import static org.sakaiproject.nakamura.api.lite.StorageClientUtils.toStringArray;
import static org.sakaiproject.nakamura.api.user.UserConstants.PROP_GROUP_MANAGERS;
import static org.sakaiproject.nakamura.api.user.UserConstants.PROP_MANAGED_GROUP;
import static org.sakaiproject.nakamura.api.user.UserConstants.PROP_MANAGERS_GROUP;

import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class SakaiGroupProcessorTest {
  private static final String GROUP_ID = "faculty";
  private SakaiGroupProcessor sakaiGroupProcessor;
  private Repository repository;
  private Session session;
  private Group group;
  private AuthorizableManager authorizableManager;

  @Before
  public void setUp() throws ClientPoolException, StorageClientException, AccessDeniedException, ClassNotFoundException {
    BaseMemoryRepository baseMemoryRepository = new BaseMemoryRepository();
    repository = baseMemoryRepository.getRepository();
    assertNotNull(repository);
    session = repository.loginAdministrative();
    assertNotNull(session);
    authorizableManager = session.getAuthorizableManager();
    assertNotNull(authorizableManager);
    authorizableManager.createGroup(GROUP_ID, GROUP_ID, new HashMap<String,Object>());
    group = (Group) authorizableManager.findAuthorizable(GROUP_ID);
    assertNotNull(group);
    sakaiGroupProcessor = new SakaiGroupProcessor();
  }

  @After
  public void teardown() throws ClientPoolException {
    session.logout();
  }

  @Test
  public void managersGroupCreated() throws Exception {
    sakaiGroupProcessor.process(group, session, new Modification(ModificationType.CREATE, "", ""),
        new HashMap<String, Object[]>());
    group = (Group) authorizableManager.findAuthorizable(GROUP_ID);
    String managersGroupId = StorageClientUtils.toString(group.getProperty(PROP_GROUP_MANAGERS));
    assertNotNull(managersGroupId);
    assertEquals(managersGroupId, StorageClientUtils.toString(group.getProperty(PROP_MANAGERS_GROUP)));
    List<String> membersList = Arrays.asList(group.getMembers());
    assertTrue(membersList.contains(managersGroupId));
    Group managersGroup = (Group) authorizableManager.findAuthorizable(managersGroupId);
    assertEquals(managersGroupId, StorageClientUtils.toString(managersGroup.getProperty(PROP_GROUP_MANAGERS)));
    assertEquals(GROUP_ID, StorageClientUtils.toString(managersGroup.getProperty(PROP_MANAGED_GROUP)));
  }

  @Test
  public void canModifyManagersMembership() throws Exception {
    assertTrue(authorizableManager.createUser("jane", "jane", "XXX", new HashMap<String,Object>()));
    assertTrue(authorizableManager.createUser("jean", "jean", "XXX", new HashMap<String,Object>()));
    assertTrue(authorizableManager.createUser("joe", "joe", "XXX", new HashMap<String,Object>()));
    assertTrue(authorizableManager.createUser("jim", "jim", "XXX", new HashMap<String,Object>()));
    String managersGroupId = GROUP_ID + "-managers";
    authorizableManager.createGroup(managersGroupId, managersGroupId, new HashMap<String,Object>());
    Group managersGroup = (Group) authorizableManager.findAuthorizable(managersGroupId);
    managersGroup.addMember("joe");
    managersGroup.addMember("jim");
    authorizableManager.updateAuthorizable(managersGroup);

    group.setProperty(PROP_MANAGERS_GROUP, managersGroupId);
    authorizableManager.updateAuthorizable(group);

    Map<String, Object[]> parameters = new HashMap<String, Object[]>();
    parameters.put(SakaiGroupProcessor.PARAM_ADD_TO_MANAGERS_GROUP,
        new String[] {"jane", "jean"});
    parameters.put(SakaiGroupProcessor.PARAM_REMOVE_FROM_MANAGERS_GROUP,
        new String[] {"joe", "jim"});
    sakaiGroupProcessor.process(group, session, new Modification(ModificationType.MODIFY, "", ""),
        parameters);
    managersGroup = (Group) authorizableManager.findAuthorizable(managersGroupId);
    List<String> membersList = Arrays.asList(managersGroup.getMembers());
    assertTrue(membersList.contains("jane"));
    assertTrue(membersList.contains("jean"));
    assertFalse(membersList.contains("joe"));
    assertFalse(membersList.contains("jim"));
  }

  @Test
  public void existingRightsArePreserved() throws Exception {
    String someoneWithManagerPrivs = "joe";
    assertTrue(authorizableManager.createUser(someoneWithManagerPrivs, someoneWithManagerPrivs, "XXX", new HashMap<String,Object>()));
    group.setProperty(PROP_GROUP_MANAGERS, toStore(new String[] {someoneWithManagerPrivs}));
    authorizableManager.updateAuthorizable(group);

    sakaiGroupProcessor.process(group, session, new Modification(ModificationType.CREATE, "", ""),
        new HashMap<String, Object[]>());
    group = (Group) authorizableManager.findAuthorizable(GROUP_ID);
    String[] managerPrivsArray = toStringArray(group.getProperty(PROP_GROUP_MANAGERS));
    assertNotNull(managerPrivsArray);
    List<String> managerPrivsList = Arrays.asList(managerPrivsArray);
    assertEquals(2, managerPrivsList.size());
    assertTrue(managerPrivsList.contains(someoneWithManagerPrivs));
  }

  @Test
  public void managersGroupDeleted() throws Exception {
    String managersGroupId = GROUP_ID + "-managers";
    assertTrue(authorizableManager.createGroup(managersGroupId, managersGroupId,
        new HashMap<String,Object>()));
    group.setProperty(PROP_MANAGERS_GROUP, managersGroupId);
    authorizableManager.updateAuthorizable(group);

    sakaiGroupProcessor.process(group, session, new Modification(ModificationType.DELETE, "", ""),
        new HashMap<String, Object[]>());
    assertNull(authorizableManager.findAuthorizable(managersGroupId));
  }

}
