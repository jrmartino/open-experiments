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
package org.sakaiproject.nakamura.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;

/**
 *
 */
public class PersonalUtils {

  /**
   * Property name for the e-mail property of a user's profile
   */
  public static final String PROP_EMAIL_ADDRESS = "email";
  
  /**
   * The base location for the Home space. 
   */
  public static final String PATH_HOME = "a:";
  
  /**
   * The node name of the authentication profile in public space.
   */
  public static final String PATH_AUTH_PROFILE = "authprofile";
  
  /**
   * The name of the private folder
   */
  public static final String PATH_PRIVATE = "private";
  
  /**
   * The name of the public folder
   */
  public static final String PATH_PUBLIC = "public";

  /**
   * Property name for the user's preferred means of message delivery
   */
  public static final String PROP_PREFERRED_MESSAGE_TRANSPORT = "preferredMessageTransport";

  private static final Logger LOGGER = LoggerFactory.getLogger(PersonalUtils.class);

  /**
   * @param au
   *          The authorizable to get the hashed path for.
   * @return The hashed path (ex: admin)
   * @throws RepositoryException
   */
  public static String getUserHashedPath(Authorizable au) {
      if ( au.hasProperty("path")) {
        return StorageClientUtils.toString(au.getProperty("path"));
      } else {
        return PATH_HOME+au.getId();
      }
  }

  public static String getPrimaryEmailAddress(Node profileNode)
      throws RepositoryException {
    String addr = null;
    if (profileNode.hasProperty(PROP_EMAIL_ADDRESS)) {
      Value[] addrs = JcrUtils.getValues(profileNode, PROP_EMAIL_ADDRESS);
      if (addrs.length > 0) {
        addr = addrs[0].getString();
      }
    }
    return addr;
  }

  public static String[] getEmailAddresses(Node profileNode) throws RepositoryException {
    String[] addrs = null;
    if (profileNode.hasProperty(PROP_EMAIL_ADDRESS)) {
      Value[] vaddrs = JcrUtils.getValues(profileNode, PROP_EMAIL_ADDRESS);
      addrs = new String[vaddrs.length];
      for (int i = 0; i < addrs.length; i++) {
        addrs[i] = vaddrs[i].getString();
      }
    }
    return addrs;
  }

  public static String getPreferredMessageTransport(Node profileNode)
      throws RepositoryException {
    String transport = null;
    if (profileNode.hasProperty(PROP_PREFERRED_MESSAGE_TRANSPORT)) {
      transport = profileNode.getProperty(PROP_PREFERRED_MESSAGE_TRANSPORT).getString();
    }
    return transport;
  }

  /**
   * @param au
   *          The authorizable to get the authprofile path for.
   * @return The absolute path in JCR to the authprofile node that contains all the
   *         profile information.
   */
  public static String getProfilePath(Authorizable au) {
    return getPublicPath(au) + "/" + PATH_AUTH_PROFILE;
  }
  public static String getProfilePath(String au) {
    return getPublicPath(au) + "/" + PATH_AUTH_PROFILE;
  }

  /**
   * @param au
   *          The authorizable to get the private path for.
   * @return The absolute path in JCR to the private folder in the user his home folder.
   */
  public static String getPrivatePath(Authorizable au) {
    return getHomePath(au) + "/" + PATH_PRIVATE;
  }

  public static String getPrivatePath(String au) {
    return getHomePath(au) + "/" + PATH_PRIVATE;
  }

  /**
   * @param au
   *          The authorizable to get the public path for.
   * @return The absolute path in JCR to the public folder in the user his home folder.
   */
  public static String getPublicPath(Authorizable au) {
    return getHomePath(au) + "/" + PATH_PUBLIC;
  }

  public static String getPublicPath(String au) {
    return getHomePath(au) + "/" + PATH_PUBLIC;
  }

  /**
   * Get the home folder for an authorizable. If the authorizable is a user, this might
   * return: a:userId
   * 
   * @param au
   *          The authorizable to get the home folder for.
   * @return The absolute path in Sparse to the home folder for an authorizable.
   */
  public static String getHomePath(Authorizable au) {
    if ( au != null ) {
      return PATH_HOME+au.getId();
    }
    return null;
  }
  public static String getHomePath(String userId) {
    if ( userId != null ) {
      return PATH_HOME+userId;
    }
    return null;
  }

  /**
   * @param session
   *          The Sparse session.
   * @param id
   *          The id of an authorizable.
   * @return An authorizable that represents a person.
   * @throws StorageClientException
   */
  // TODO: review this as it hides the AccessDeniedException. I think this is dangerous ieb - 20110121
  public static Authorizable getAuthorizable(Session session, String id)
      throws StorageClientException {
    Authorizable authorizable = null;
    try {
      authorizable = session.getAuthorizableManager().findAuthorizable(id);
    } catch (AccessDeniedException e) {
      LOGGER.error("Access denied to the Authorizable object for ID: [" + id + "].");
      throw new StorageClientException(e.getMessage());
    }
    return authorizable;
  }

}
