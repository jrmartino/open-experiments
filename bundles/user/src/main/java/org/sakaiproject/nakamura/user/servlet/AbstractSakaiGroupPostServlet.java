/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.nakamura.user.servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.api.user.UserConstants.Joinable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;

/**
 * Base class for servlets manipulating groups
 */
public abstract class AbstractSakaiGroupPostServlet extends
    AbstractAuthorizablePostServlet {
  private static final long serialVersionUID = 1159063041816944076L;

  /**
   * The Sparse Repository we access to resolve resources
   * 
   * @scr.reference
   */
  protected transient Repository repository;

  private static final Logger LOGGER = LoggerFactory
      .getLogger(AbstractSakaiGroupPostServlet.class);

  /**
   * Update the group membership based on the ":member" request parameters. If the
   * ":member" value ends with @Delete it is removed from the group membership, otherwise
   * it is added to the group membership.
   * 
   * @param request
   * @param authorizable
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  protected void updateGroupMembership(SlingHttpServletRequest request,
      Authorizable authorizable, List<Modification> changes) throws ServletException,
      StorageClientException, AccessDeniedException {
    updateGroupMembership(request, authorizable, SlingPostConstants.RP_PREFIX + "member",
        changes);
  }

  protected void updateGroupMembership(SlingHttpServletRequest request,
      Authorizable authorizable, String paramName, List<Modification> changes)
      throws ServletException, StorageClientException, AccessDeniedException {
    if (authorizable instanceof Group) {
      Group group = ((Group) authorizable);
      String groupPath = UserConstants.SYSTEM_USER_MANAGER_GROUP_PREFIX + group.getId();

      ResourceResolver resolver = request.getResourceResolver();
      Resource baseResource = request.getResource();
      boolean changed = false;

      final Session session = request.getResourceResolver().adaptTo(Session.class);
      final AuthorizableManager userManager = session.getAuthorizableManager();

      // first remove any members posted as ":member@Delete"
      String[] membersToDelete = request.getParameterValues(paramName
          + SlingPostConstants.SUFFIX_DELETE);
      if (membersToDelete != null) {
        for (String member : membersToDelete) {
          Authorizable memberAuthorizable = getAuthorizable(baseResource, member,
              userManager, resolver);
          if (memberAuthorizable != null) {
            if (!UserConstants.ANON_USERID.equals(resolver.getUserID())
                && memberAuthorizable.getId().equals(resolver.getUserID())) {
              // since the current user is the member being removed,
              // we can grab admin session since user should be able to delete themselves
              // from a group
              Session adminSession = getAdminSession();
              try {
                final AuthorizableManager adminManager = adminSession
                    .getAuthorizableManager();
                Group adminAuthGroup = (Group) adminManager.findAuthorizable(group
                    .getId());
                if (adminAuthGroup != null) {
                  adminAuthGroup.removeMember(memberAuthorizable.getId());
                  adminManager.updateAuthorizable(adminAuthGroup);
                  changed = true;
                }

              } finally {
                ungetSession(adminSession);
              }
            } else {
              // current user is not the member being removed
              group.removeMember(memberAuthorizable.getId());
              userManager.updateAuthorizable(group);
              changed = true;
            }
          }
        }
      }

      Joinable groupJoin = getJoinable(group);

      // second add any members posted as ":member"
      String[] membersToAdd = request.getParameterValues(paramName);
      if (membersToAdd != null) {
        Group peerGroup = getPeerGroupOf(group, userManager);
        List<Authorizable> membersToRemoveFromPeer = new ArrayList<Authorizable>();
        for (String member : membersToAdd) {
          Authorizable memberAuthorizable = getAuthorizable(baseResource, member,
              userManager, resolver);
          if (memberAuthorizable != null) {
            if (!UserConstants.ANON_USERID.equals(resolver.getUserID())
                && Joinable.yes.equals(groupJoin)
                && memberAuthorizable.getId().equals(resolver.getUserID())) {
              // we can grab admin session since group allows all users to join
              Session adminSession = getAdminSession();
              try {
                AuthorizableManager adminManager = adminSession.getAuthorizableManager();
                Group adminAuthGroup = (Group) adminManager.findAuthorizable(group
                    .getId());
                if (adminAuthGroup != null) {
                  adminAuthGroup.addMember(memberAuthorizable.getId());
                  adminManager.updateAuthorizable(adminAuthGroup);
                  changed = true;
                }
              } finally {
                ungetSession(adminSession);
              }
            } else {
              // group is restricted, so use the current user's authorization
              // to add the member to the group:
              group.addMember(memberAuthorizable.getId());
              userManager.updateAuthorizable(group);
              changed = true;
            }
            if (peerGroup != null) {
              final List<String> peerGroupMembers = Arrays.asList(peerGroup.getMembers());
              if (peerGroupMembers.contains(memberAuthorizable.getId())) {
                membersToRemoveFromPeer.add(memberAuthorizable);
              }
            }
          }
        }
        if (peerGroup != null) {
          for (Authorizable member : membersToRemoveFromPeer) {
            peerGroup.removeMember(member.getId());
            userManager.updateAuthorizable(peerGroup);
          }
        }
      }

      if (changed) {
        // add an entry to the changes list to record the membership
        // change
        changes.add(Modification.onModified(groupPath + "/members"));
      }
    }
  }

  private Group getPeerGroupOf(Group group, AuthorizableManager userManager)
      throws ServletException, AccessDeniedException, StorageClientException {
    Group peerGroup = null;
    if (group.hasProperty(UserConstants.PROP_MANAGERS_GROUP)) {
      final String managersGroupId = StorageClientUtils.toString(group
          .getProperty(UserConstants.PROP_MANAGERS_GROUP));
      peerGroup = (Group) userManager.findAuthorizable(managersGroupId);
    } else {
      if (group.hasProperty(UserConstants.PROP_MANAGED_GROUP)) {
        final String managedGroupId = StorageClientUtils.toString(group
            .getProperty(UserConstants.PROP_MANAGED_GROUP));
        peerGroup = (Group) userManager.findAuthorizable(managedGroupId);
      }
    }
    return peerGroup;
  }

  /**
   * Gets the member, assuming its a principal name, failing that it assumes it a path to
   * the resource.
   * 
   * @param member
   *          the token pointing to the member, either a name or a uri
   * @param authorizableManager
   *          the authorizableManager for this request.
   * @param resolver
   *          the resource resolver for this request.
   * @return the authorizable, or null if no authorizable was found.
   */
  private Authorizable getAuthorizable(Resource baseResource, String member,
      AuthorizableManager authorizableManager, ResourceResolver resolver) {
    Authorizable memberAuthorizable = null;
    try {
      memberAuthorizable = authorizableManager.findAuthorizable(member);
    } catch (AccessDeniedException e) {
      // if we can't find the members then it may be resolvable as a resource.
    } catch (StorageClientException e) {
      // if we can't find the members then it may be resolvable as a resource.
    }
    if (memberAuthorizable == null) {
      Resource res = resolver.getResource(baseResource, member);
      if (res != null) {
        memberAuthorizable = res.adaptTo(Authorizable.class);
      }
    }
    return memberAuthorizable;
  }

  /**
   * @param request
   *          the request
   * @param group
   *          the group
   * @param managers
   *          a list of principals who are allowed to admin the group.
   * @param changes
   *          changes made
   */
  protected void updateOwnership(SlingHttpServletRequest request, Group group,
      String[] managers, List<Modification> changes) throws ServletException {

    handleAuthorizablesOnProperty(request, group, UserConstants.PROP_GROUP_MANAGERS,
        SlingPostConstants.RP_PREFIX + "manager", managers);
    handleAuthorizablesOnProperty(request, group, UserConstants.PROP_GROUP_VIEWERS,
        SlingPostConstants.RP_PREFIX + "viewer", null);

  }

  /**
   * @param request
   *          The request that contains the authorizables.
   * @param group
   *          The group that should be modified.
   * @param propAuthorizables
   *          The name of the property on the group where the authorizable IDs should be
   *          added/deleted.
   * @param paramName
   *          The name of the parameter that contains the authorizable IDs. ie: :manager
   *          or :viewer. If :manager is specified, :manager@Delete will be used for
   *          deletes.
   * @param extraPrincipalsToAdd
   *          An array of authorizable IDs that should be added as well.
   */
  protected void handleAuthorizablesOnProperty(SlingHttpServletRequest request,
      Group group, String propAuthorizables, String paramName,
      String[] extraPrincipalsToAdd) throws ServletException {
    Set<String> principals = new HashSet<String>();
    if (group.hasProperty(propAuthorizables)) {
      String[] existingPrincipals = StorageClientUtils.toStringArray(group
          .getProperty(propAuthorizables));
      for (String principal : existingPrincipals) {
        principals.add(principal);
      }
    }

    boolean changed = false;

    // Remove all the managers that are in the :manager@Delete request parameter.
    String[] principalsToDelete = request.getParameterValues(paramName
        + SlingPostConstants.SUFFIX_DELETE);
    if (principalsToDelete != null) {
      for (String principal : principalsToDelete) {
        principals.remove(principal);
        changed = true;
      }
    }

    // Add the new ones (if any)
    String[] principalsToAdd = request.getParameterValues(paramName);
    if (principalsToAdd != null) {
      for (String principal : principalsToAdd) {
        principals.add(principal);
        changed = true;
      }
    }

    // Add the extra ones (if any.)
    if (extraPrincipalsToAdd != null) {
      for (String principal : extraPrincipalsToAdd) {
        principals.add(principal);
        changed = true;
      }
    }

    // Write the property.
    if (changed) {
      String[] newPrincipals = new String[principals.size()];
      int i = 0;
      for (String principal : principals) {
        newPrincipals[i++] = principal;
      }
      group.setProperty(propAuthorizables, newPrincipals);
    }
  }

  /** Returns the sparse repository used by this service. */
  protected Repository getRepository() {
    return repository;
  }

  /**
   * Returns an administrative session to the default workspace.
   */
  protected Session getAdminSession() {
    try {
      return getRepository().loginAdministrative();
    } catch (ClientPoolException e) {
      LOGGER.error(e.getLocalizedMessage(), e);
    } catch (StorageClientException e) {
      LOGGER.error(e.getLocalizedMessage(), e);
    } catch (AccessDeniedException e) {
      LOGGER.error(e.getLocalizedMessage(), e);
    }
    return null;
  }

  /**
   * Return the administrative session and close it.
   */
  protected void ungetSession(final Session session) {
    if (session != null) {
      try {
        session.logout();
      } catch (Throwable t) {
        LOGGER.error("Unable to log out of session: " + t.getMessage(), t);
      }
    }
  }

  /**
   * @return true if the authz group is joinable
   */
  public Joinable getJoinable(Authorizable authorizable) {
    if (authorizable instanceof Group
        && authorizable.hasProperty(UserConstants.PROP_JOINABLE_GROUP)) {
      try {
        final String[] joinable = StorageClientUtils.toStringArray(authorizable
            .getProperty(UserConstants.PROP_JOINABLE_GROUP));
        if (joinable != null && joinable.length > 0)
          return Joinable.valueOf(joinable[0]);
      } catch (IllegalArgumentException e) {
        // ignore
      }
    }
    return Joinable.no;
  }

}
