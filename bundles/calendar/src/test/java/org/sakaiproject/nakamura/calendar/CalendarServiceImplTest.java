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


import static org.apache.sling.jcr.resource.JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.sakaiproject.nakamura.api.calendar.CalendarConstants.SAKAI_CALENDAR_RT;
import static org.sakaiproject.nakamura.api.calendar.CalendarConstants.SIGNUP_NODE_RT;

import com.google.common.collect.ImmutableMap;

import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Uid;

import org.apache.sling.commons.testing.jcr.MockNode;
import org.junit.Before;
import org.junit.Test;
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
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.lite.RepositoryImpl;
import org.sakaiproject.nakamura.util.IOUtils;

import java.io.FileReader;
import java.io.InputStream;
import java.io.Reader;

import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;


/**
 *
 */
public class CalendarServiceImplTest {

  private CalendarServiceImpl service;
  private MockNode calendarNode;
  private CalendarSubPathProducer producer;
  private Uid uid;
  private Content eventNode;
  private Content signupNode;
  private Session session;
  private BaseMemoryRepository baseMemoryRepository;
  private RepositoryImpl sparseRepository;
  
  public CalendarServiceImplTest() throws ClientPoolException, StorageClientException, AccessDeniedException, ClassNotFoundException {
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
    session.logout();
    session = sparseRepository.loginAdministrative("ieb");
  }

  @Before
  public void setUp() throws ClientPoolException, StorageClientException, AccessDeniedException, ClassNotFoundException {
    
    service = new CalendarServiceImpl();
    uid = new Uid("DD45F1DB-34ED-4BBE-A2B2-970E0668F7BC");
    PropertyList pList = new PropertyList();
    pList.add(uid);
    producer = new CalendarSubPathProducer(new VEvent(pList));
  }

  @Test
  public void testStoringCalendarInputStream() throws Exception {
    InputStream in = getClass().getClassLoader().getResourceAsStream("home.ics");
    // Do mocking
    String path = "a:ieb/path/to/store/calendar";
    // Store nodes
    service.store(in, session, path);
    // Verify if everything is correct.
    verifyMocks();
  }

  @Test
  public void testStoringCalendarString() throws Exception {
    InputStream in = getClass().getClassLoader().getResourceAsStream("home.ics");
    String calendar = IOUtils.readFully(in, "UTF-8");
    // Do mocking
    String path = "a:ieb/path/to/store/calendar";
    // Store nodes
    service.store(calendar, session, path);
    // Verify if everything is correct.
    verifyMocks();
  }

  @Test
  public void testStoringCalendarReader() throws Exception {
    String fileName = getClass().getClassLoader().getResource("home.ics").getFile();
    Reader r = new FileReader(fileName);
    // Do mocking
    String path = "a:ieb/path/to/store/calendar";
    // Store nodes
    service.store(r, session, path);
    // Verify if everything is correct.
    verifyMocks();
  }

  @Test
  public void testStoringCalendarException() {
    try {
      service.store("foo", null, null);
      fail("Should have thrown an exception.");
    } catch (CalendarException e) {
      assertEquals(500, e.getCode());
    }
  }

  /**
   * @throws RepositoryException
   * @throws ValueFormatException
   * 
   */
  private void verifyMocks() throws Exception {
    // TODO: Verify all is correct.

  }


}
