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

import static org.sakaiproject.nakamura.api.calendar.CalendarConstants.PARTICIPANTS_NODE_NAME;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.sakaiproject.nakamura.api.calendar.CalendarException;
import org.sakaiproject.nakamura.api.calendar.signup.SignupPreProcessor;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;

/**
 * Checks if a signup event has a maximum number of participants.
 */
@Service
@Component(immediate = true)
public class MaxParticipantsSignupPreProcessors implements SignupPreProcessor {

  protected static final String SAKAI_EVENT_MAX_PARTICIPANTS = "sakai:event-max-participants";
  public static final Logger LOGGER = LoggerFactory
      .getLogger(MaxParticipantsSignupPreProcessors.class);

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.calendar.signup.SignupPreProcessor#checkSignup(org.apache.sling.api.SlingHttpServletRequest,
   *      javax.jcr.Node)
   */
  public void checkSignup(SlingHttpServletRequest request, Content signupNode)
  throws CalendarException {
    // Get the number of maximum participants.
    Session session = StorageClientUtils.adaptToSession(request.getResourceResolver()
        .adaptTo(javax.jcr.Session.class));
    if (signupNode.hasProperty(SAKAI_EVENT_MAX_PARTICIPANTS)) {
      long maxParticipants = StorageClientUtils.toLong(signupNode
          .getProperty(SAKAI_EVENT_MAX_PARTICIPANTS));

      // If a valid number is set, we check it.
      // -1 or smaller means we don't.
      if (maxParticipants > 0) {
        checkParticipants(signupNode, maxParticipants, session);
      }
    }

  }

  /**
   * @param signupNode
   * @param maxParticipants
   * @param session2
   * @throws CalendarException
   */
  protected void checkParticipants(Content signupNode, long maxParticipants,
      Session session) throws CalendarException {

    // Check how many participants there are in this event.
    // We do this by doing a query for the participants.
    try {
      String path = signupNode.getPath() + "/" + PARTICIPANTS_NODE_NAME;
      Content content = session.getContentManager().get(path);
      if (content != null) {
        long count = 0;
        for (String p : content.listChildPaths()) {
          count++;
          if (count > maxParticipants) {
            throw new CalendarException(HttpServletResponse.SC_BAD_REQUEST,
                "This event has reached the maximum number of participants.");
          }
        }
      }

    } catch (StorageClientException e) {
      LOGGER
          .error(
              "Caught a repository exception when trying to get the number of participants for a calendar event.",
              e);
      throw new CalendarException(500, e.getMessage());
    } catch (AccessDeniedException e) {
      LOGGER
          .error(
              "Caught a repository exception when trying to get the number of participants for a calendar event.",
              e);
      throw new CalendarException(403, e.getMessage());
    }

  }

}
