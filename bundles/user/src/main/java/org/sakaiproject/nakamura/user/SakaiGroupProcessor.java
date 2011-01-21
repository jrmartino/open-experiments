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

import static org.sakaiproject.nakamura.api.lite.StorageClientUtils.toStore;
import static org.sakaiproject.nakamura.api.lite.StorageClientUtils.toStringArray;
import static org.sakaiproject.nakamura.api.user.UserConstants.PROP_GROUP_MANAGERS;
import static org.sakaiproject.nakamura.api.user.UserConstants.PROP_MANAGED_GROUP;
import static org.sakaiproject.nakamura.api.user.UserConstants.PROP_MANAGERS_GROUP;

import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.user.AuthorizablePostProcessor;
import org.sakaiproject.nakamura.api.user.SparseAuthorizablePostProcessor;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class handles whatever processing is needed before the Jackrabbit Group modification
 * can be sent to other AuthorizablePostProcessor services.
 */
public class SakaiGroupProcessor extends AbstractAuthorizableProcessor implements
SparseAuthorizablePostProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(SakaiGroupProcessor.class);
  public static final String PARAM_ADD_TO_MANAGERS_GROUP = SlingPostConstants.RP_PREFIX + "sakai:manager";
  public static final String PARAM_REMOVE_FROM_MANAGERS_GROUP = PARAM_ADD_TO_MANAGERS_GROUP + SlingPostConstants.SUFFIX_DELETE;

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.user.AuthorizablePostProcessor#process(org.sakaiproject.nakamura.api.lite.authorizable.Authorizable, org.sakaiproject.nakamura.api.lite.Session, org.apache.sling.servlets.post.Modification, java.util.Map)
   */
  public void process(Authorizable authorizable, Session session, Modification change,
      Map<String, Object[]> parameters) throws Exception {
    if (authorizable instanceof Group) {
      Group group = (Group) authorizable;
      if (change.getType() == ModificationType.DELETE) {
        deleteManagersGroup(group, session);
      } else {
        if (change.getType() == ModificationType.CREATE) {
          createManagersGroup(group, session);
        }
        updateManagersGroupMembership(group, session, parameters);
      }
    }
  }

  /**
   * Generate a private self-managed Jackrabbit Group to hold Sakai group
   * members with the Manager role. Such members have all access
   * rights over the Sakai group itself and may be given special access
   * rights to content.
   * @throws AccessDeniedException
   */
  private void createManagersGroup(Group group, Session session) throws StorageClientException, AccessDeniedException {
    AuthorizableManager authorizableManager = session.getAuthorizableManager();
    String managersGroupId = makeUniqueAuthorizableId(group.getId() + "-managers", authorizableManager);
    Map<String, Object> managersProperties = new HashMap<String, Object>();

    // TODO Find out if the old Nakamura-specific rep:group-managers and rep:group-viewers
    // pseudo-permission properties have been replaced by normal ACLs on the Group record's
    // storage.

    // The Group Managers administer their own membership as well as the main Group's membership.
    managersProperties.put(PROP_GROUP_MANAGERS, toStore(managersGroupId));
    managersProperties.put(PROP_MANAGED_GROUP, toStore(group.getId()));

    boolean isSuccess = authorizableManager.createGroup(managersGroupId, managersGroupId, managersProperties);
    if (!isSuccess) {
      throw new StorageClientException("Failed to generate Managers Group " + managersGroupId);
    }
    Group managersGroup = (Group) authorizableManager.findAuthorizable(managersGroupId);
    if (managersGroup == null) {
      throw new StorageClientException("Failed to retrieve Managers Group " + managersGroupId);
    }

    // Add the managers group to its Sakai group.
    group.addMember(managersGroupId);

    // Have the managers group manage the Sakai group.
    String[] managerPrivilegedArray = toStringArray(group.getProperty(PROP_GROUP_MANAGERS));
    Set<String> managerPrivilegedSet = new HashSet<String>();
    if (managerPrivilegedArray != null) {
      managerPrivilegedSet.addAll(Arrays.asList(managerPrivilegedArray));
    }
    managerPrivilegedSet.add(managersGroupId);
    group.setProperty(PROP_GROUP_MANAGERS, toStore(managerPrivilegedSet.toArray(new String[managerPrivilegedSet.size()])));

    // Set the association between the two groups.
    group.setProperty(PROP_MANAGERS_GROUP, toStore(managersGroupId));

    // Update all.
    authorizableManager.updateAuthorizable(managersGroup);
    authorizableManager.updateAuthorizable(group);
  }

  /**
   * @param group
   * @param authorizableManager
   * @return the group that holds the group's Manager members, or null if there is no
   *         managers group or it is inaccessible
   * @throws AccessDeniedException
   * @throws StorageClientException
   */
  private Group getManagersGroup(Group group, AuthorizableManager authorizableManager) throws AccessDeniedException, StorageClientException {
    Group managersGroup = null;
    if (group.hasProperty(UserConstants.PROP_MANAGERS_GROUP)) {
      String managersGroupId = StorageClientUtils.toString(group.getProperty(UserConstants.PROP_MANAGERS_GROUP));
      managersGroup = (Group) authorizableManager.findAuthorizable(managersGroupId);
    }
    return managersGroup;
  }

  /**
   * Inspired by Sling's AbstractCreateOperation ensureUniquePath.
   * @param startId
   * @param authorizableManager
   * @return
   * @throws AccessDeniedException
   * @throws StorageClientException
   */
  private String makeUniqueAuthorizableId(String startId, AuthorizableManager authorizableManager) throws AccessDeniedException, StorageClientException {
    String newAuthorizableId = startId;
    int idx = 0;
    while (authorizableManager.findAuthorizable(newAuthorizableId) != null) {
      if (idx > 100) {
        throw new StorageClientException("Too much contention on authorizable ID " + startId);
      } else {
        newAuthorizableId = startId + "_" + idx++;
      }
    }
    return newAuthorizableId;
  }

  private void updateManagersGroupMembership(Group group, Session session, Map<String, Object[]> parameters)
      throws AccessDeniedException, StorageClientException {
    AuthorizableManager authorizableManager = session.getAuthorizableManager();
    Group managersGroup = getManagersGroup(group, authorizableManager);
    if (managersGroup != null) {
      Object[] addValues = parameters.get(PARAM_ADD_TO_MANAGERS_GROUP);
      if ((addValues != null) && (addValues instanceof String[])) {
        for (String memberId : (String [])addValues) {
          Authorizable authorizable = authorizableManager.findAuthorizable(memberId);
          if (authorizable != null) {
            managersGroup.addMember(memberId);
          } else {
            LOGGER.warn("Could not add {} to managers group {}", memberId, managersGroup.getId());
          }
        }
      }
      Object[] removeValues = parameters.get(PARAM_REMOVE_FROM_MANAGERS_GROUP);
      if ((removeValues != null) && (removeValues instanceof String[])) {
        for (String memberId : (String [])removeValues) {
          Authorizable authorizable = authorizableManager.findAuthorizable(memberId);
          if (authorizable != null) {
            managersGroup.removeMember(memberId);
          } else {
            LOGGER.warn("Could not remove {} from managers group {}", memberId, managersGroup.getId());
          }
        }
      }
      authorizableManager.updateAuthorizable(managersGroup);
    }
  }

  private void deleteManagersGroup(Group group, Session session) throws AccessDeniedException, StorageClientException {
    AuthorizableManager authorizableManager = session.getAuthorizableManager();
    Group managersGroup = getManagersGroup(group, authorizableManager);
    if (managersGroup != null) {
      LOGGER.debug("Deleting managers group {} as part of deleting {}", managersGroup.getId(), group.getId());
      authorizableManager.delete(managersGroup.getId());
    }
  }
}
