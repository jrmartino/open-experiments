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
package org.sakaiproject.nakamura.calendar;

import net.fortuna.ical4j.model.Calendar;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.sakaiproject.nakamura.api.calendar.CalendarConstants;
import org.sakaiproject.nakamura.api.calendar.CalendarException;
import org.sakaiproject.nakamura.api.calendar.CalendarService;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.user.AuthorizablePostProcessor;
import org.sakaiproject.nakamura.api.user.SparseAuthorizablePostProcessor;
import org.sakaiproject.nakamura.util.PersonalUtils;

import java.util.Map;

/**
 * Creates a Calendar for each user/group.
 */
@Service
@Component(immediate = true)
@Properties(value = {
    @Property(name = "service.ranking", intValue=10)})
public class CalendarAuthorizablePostProcessor implements SparseAuthorizablePostProcessor {
  @Reference
  protected transient CalendarService calendarService;

  public void process(
      org.sakaiproject.nakamura.api.lite.authorizable.Authorizable authorizable,
      org.sakaiproject.nakamura.api.lite.Session session, Modification change,
      Map<String, Object[]> parameters) throws Exception {
    if (ModificationType.CREATE.equals(change.getType())) {
      // Store it.
      createCalendar(authorizable, session);
    }
  }

  private void createCalendar(
      org.sakaiproject.nakamura.api.lite.authorizable.Authorizable authorizable,
      org.sakaiproject.nakamura.api.lite.Session session) throws StorageClientException, AccessDeniedException, CalendarException {
    // The path to the calendar of an authorizable.
    String path = PersonalUtils.getHomePath(authorizable);
    path += "/" + CalendarConstants.SAKAI_CALENDAR_NODENAME;

    Calendar cal = new Calendar();
    calendarService.store(cal, session, path);
    
    session.getAccessControlManager().setAcl(Security.ZONE_CONTENT, path, new AclModification[]{
        new AclModification(AclModification.grantKey(authorizable.getId()), Permissions.ALL.getPermission(), Operation.OP_REPLACE )
    });
  }


}
