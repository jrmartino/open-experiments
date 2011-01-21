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
import static org.sakaiproject.nakamura.api.calendar.CalendarConstants.SAKAI_CALENDAR_PROPERTY_PREFIX;
import static org.sakaiproject.nakamura.api.calendar.CalendarConstants.SAKAI_CALENDAR_RT;
import static org.sakaiproject.nakamura.api.calendar.CalendarConstants.SIGNUP_NODE_NAME;
import static org.sakaiproject.nakamura.api.calendar.CalendarConstants.SIGNUP_NODE_RT;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Lists;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.DateProperty;

import org.apache.felix.scr.annotations.Service;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.sakaiproject.nakamura.api.calendar.CalendarException;
import org.sakaiproject.nakamura.api.calendar.CalendarService;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Date;
import java.util.Iterator;
import java.util.List;


/**
 *
 */
@org.apache.felix.scr.annotations.Component(immediate = true)
@Service(value = CalendarService.class)
public class CalendarServiceImpl implements CalendarService {

  public static final Logger LOGGER = LoggerFactory.getLogger(CalendarServiceImpl.class);

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.calendar.CalendarService#export(org.sakaiproject.nakamura.api.lite.content.Content)
   */
  public Calendar export(Content contentNode) throws CalendarException {
    return export(contentNode, new String[] { VEvent.VEVENT });
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.calendar.CalendarService#export(org.sakaiproject.nakamura.api.lite.content.Content, java.lang.String[])
   */
  public Calendar export(Content contentNode, String[] types) throws CalendarException {
    throw new UnsupportedOperationException("Not Yet Implemented, plesae port to Sparse");
    /*
    // Start constructing the iCal Calendar.
    Calendar c = new Calendar();
    try {
      String path = contentNode.getPath();
      Session session = node.getSession();

      // Do a query under this node for all the sakai/calendar-event nodes.
      StringBuilder sb = new StringBuilder("/jcr:root");
      sb.append(path);
      sb.append("//*[");
      for (int i = 0; i < types.length; i++) {
        String type = types[i];
        sb.append("@sling:resourceType='");
        sb.append(SAKAI_CALENDAR_RT).append("-").append(type.toLowerCase()).append("'");
        if (i < (types.length - 1)) {
          sb.append(" or ");
        }
      }
      sb.append("]");
      String queryString = sb.toString();

      // Perform the query
      QueryManager qm = session.getWorkspace().getQueryManager();
      Query q = qm.createQuery(queryString, Query.XPATH);
      QueryResult result = q.execute();
      NodeIterator nodes = result.getNodes();

      PropertyFactory propFactory = PropertyFactoryImpl.getInstance();

      // Each node represents a VEVENT.
      while (nodes.hasNext()) {
        Node resultNode = nodes.nextNode();
        VEvent event = new VEvent();
        PropertyIterator props = resultNode.getProperties(SAKAI_CALENDAR_PROPERTY_PREFIX
            + "*");
        while (props.hasNext()) {
          javax.jcr.Property p = props.nextProperty();
          // Get the name of the property but strip out the sakai:vcal-*
          String name = p.getName().substring(11);

          // Create an iCal property and add it to the event.
          Property calProp = propFactory.createProperty(name);
          Value v = p.getValue();
          String value = v.getString();
          if (v.getType() == PropertyType.DATE) {
            value = DateUtils.rfc2445(v.getDate().getTime());
          }

          calProp.setValue(value);
          event.getProperties().add(calProp);

        }
        // Add the event to the calendar.
        c.getComponents().add(event);

      }

    } catch (RepositoryException e) {
      LOGGER.error("Caught a repositoryException when trying to export a calendar", e);
      throw new CalendarException(500, e.getMessage());
    } catch (IOException e) {
      LOGGER.error("Caught an IOException when trying to export a calendar", e);
      throw new CalendarException(500, e.getMessage());
    } catch (URISyntaxException e) {
      LOGGER.error("Caught a URISyntaxException when trying to export a calendar", e);
      throw new CalendarException(500, e.getMessage());
    } catch (ParseException e) {
      LOGGER.error("Caught a ParseException when trying to export a calendar", e);
      throw new CalendarException(500, e.getMessage());
    }

    return c;
    */
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.calendar.CalendarService#store(net.fortuna.ical4j.model.Calendar,
   *      javax.jcr.Session, java.lang.String)
   */
  public Content store(Calendar calendar, Session session, String path)
      throws CalendarException {
    Content calendarNode = null;
    try {
      ContentManager cm = session.getContentManager();
      if ( cm.exists(path)) {
        calendarNode = cm.get(path);
        calendarNode.setProperty(SLING_RESOURCE_TYPE_PROPERTY, SAKAI_CALENDAR_RT);

        // Store all the properties of the calendar on the node.
        @SuppressWarnings("rawtypes")
        Iterator it = calendar.getProperties().iterator();
        while (it.hasNext()) {
          Property p = (Property) it.next();
          calendarNode.setProperty(p.getName(), StorageClientUtils.toStore(p.getValue()));
        }
      } else {
        Builder<String, Object> b = ImmutableMap.builder();
        b.put(SLING_RESOURCE_TYPE_PROPERTY, StorageClientUtils.toStore(SAKAI_CALENDAR_RT));
        @SuppressWarnings("rawtypes")
        Iterator it = calendar.getProperties().iterator();
        while (it.hasNext()) {
          Property p = (Property) it.next();
          b.put(p.getName(), StorageClientUtils.toStore(p.getValue()));
        }
        
        calendarNode = new Content(path,b.build());
        
      }
      cm.update(calendarNode);

      // Now loop over all the events and store these.
      // We could do calendar.getComponents(Component.VEVENT) but we will choose
      // everything.
      // They can be filtered in the export method.
      ComponentList list = calendar.getComponents();
      @SuppressWarnings("unchecked")
      Iterator<CalendarComponent> events = list.iterator();
      while (events.hasNext()) {
        CalendarComponent component = events.next();
        storeEvent(calendarNode, component, session);
      }

    } catch (StorageClientException e) {
      LOGGER.error("Caught a Storage when trying to store a calendar", e);
      throw new CalendarException(500, e.getMessage());
    } catch (AccessDeniedException e) {
      LOGGER.error("Permission Denied when trying to store a calendar", e);
      throw new CalendarException(403, e.getMessage());
    }

    return calendarNode;

  }

  /**
   * @param calendarNode
   * @param event
   * @throws StorageClientException 
   * @throws AccessDeniedException 
   */
  protected void storeEvent(Content calendarNode, CalendarComponent component,
      Session session) throws AccessDeniedException, StorageClientException {

    ContentManager contentManager = session.getContentManager();
    // Get the start date.
    CalendarSubPathProducer producer = new CalendarSubPathProducer(component);

    String path = calendarNode.getPath() + PathUtils.getSubPath(producer);
    Content eventNode = null;
    if (contentManager.exists(path)) {
      eventNode = contentManager.get(path);
      eventNode.setProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
          SAKAI_CALENDAR_RT + "-" + producer.getType());

      @SuppressWarnings("unchecked")
      Iterator<Property> it = component.getProperties().iterator();
      while (it.hasNext()) {
        Property p = it.next();
        if (p instanceof DateProperty) {
          Date d = ((DateProperty) p).getDate();
          java.util.Calendar value = java.util.Calendar.getInstance();
          value.setTime(d);
          eventNode.setProperty(SAKAI_CALENDAR_PROPERTY_PREFIX + p.getName(), StorageClientUtils.toStore(value));
        } else {
          eventNode.setProperty(SAKAI_CALENDAR_PROPERTY_PREFIX + p.getName(), StorageClientUtils.toStore(p.getValue()));
        }
      }
    } else {
      Builder<String, Object> b = ImmutableMap.builder();
      @SuppressWarnings("unchecked")
      Iterator<Property> it = component.getProperties().iterator();
      while (it.hasNext()) {
        Property p = it.next();
        if (p instanceof DateProperty) {
          Date d = ((DateProperty) p).getDate();
          java.util.Calendar value = java.util.Calendar.getInstance();
          value.setTime(d);
          b.put(SAKAI_CALENDAR_PROPERTY_PREFIX + p.getName(), StorageClientUtils.toStore(value));
        } else {
          b.put(SAKAI_CALENDAR_PROPERTY_PREFIX + p.getName(), StorageClientUtils.toStore(p.getValue()));
        }
      }
      b.put(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
          StorageClientUtils.toStore(SAKAI_CALENDAR_RT + producer.getType()));
      eventNode = new Content(path, b.build());
    }
    

    contentManager.update(eventNode);
    
    handlePrivacy(eventNode, component, session);

    // If this is an event, we add a signup node.
    String signupNodePath = path+"/"+ SIGNUP_NODE_NAME;
    if (component instanceof VEvent && !contentManager.exists(signupNodePath)) {
      contentManager.update(new Content(signupNodePath, ImmutableMap.of(SLING_RESOURCE_TYPE_PROPERTY,  StorageClientUtils.toStore(SIGNUP_NODE_RT))));
    }
  }

  /**
   * @param eventNode
   * @param component
   * @throws AccessDeniedException 
   * @throws StorageClientException 
   */
  protected void handlePrivacy(Content eventNode, CalendarComponent component, Session session) throws StorageClientException, AccessDeniedException {
    // Default = public.
    if (component.getProperty(Clazz.CLASS) != null) {
      Clazz c = (Clazz) component.getProperty(Clazz.CLASS);
      if (c == Clazz.PRIVATE) {
        List<AclModification> aclModifications = Lists.newArrayList();
        AclModification.addAcl(true, Permissions.ALL, session.getUserId(),
            aclModifications);
        AclModification.addAcl(false, Permissions.ALL, Group.EVERYONE, aclModifications);
        AclModification.addAcl(false, Permissions.ALL, User.ANON_USER, aclModifications);
        session.getAccessControlManager().setAcl(Security.ZONE_CONTENT,
            eventNode.getPath(),
            aclModifications.toArray(new AclModification[aclModifications.size()]));

      }
    }
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.calendar.CalendarService#store(java.lang.String,
   *      org.sakaiproject.nakamura.api.lite.Session, java.lang.String)
   */
  public Content store(String calendar, Session session, String path)
      throws CalendarException {
    ByteArrayInputStream in = new ByteArrayInputStream(calendar.getBytes());
    return store(in, session, path);

  }

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.calendar.CalendarService#store(java.io.InputStream,
   *      org.sakaiproject.nakamura.api.lite.Session, java.lang.String)
   */
  public Content store(InputStream in, Session session, String path)
      throws CalendarException {
    try {
      CalendarBuilder builder = new CalendarBuilder();
      Calendar calendar = builder.build(in);
      return store(calendar, session, path);
    } catch (IOException e) {
      LOGGER.error(
          "Caught an IOException when trying to store a Calendar (InputStream).", e);
      throw new CalendarException(500, e.getMessage());
    } catch (ParserException e) {
      LOGGER.error(
          "Caught a ParserException when trying to store a Calendar (InputStream).", e);
      throw new CalendarException(500, e.getMessage());
    }
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.calendar.CalendarService#store(java.io.Reader,
   *      org.sakaiproject.nakamura.api.lite.Session, java.lang.String)
   */
  public Content store(Reader reader, Session session, String path) throws CalendarException {
    try {
      CalendarBuilder builder = new CalendarBuilder();
      Calendar calendar = builder.build(reader);
      return store(calendar, session, path);
    } catch (IOException e) {
      LOGGER.error("Caught an IOException when trying to store a Calendar (Reader).", e);
      throw new CalendarException(500, e.getMessage());
    } catch (ParserException e) {
      LOGGER.error("Caught a ParserException when trying to store a Calendar (Reader).",
          e);
      throw new CalendarException(500, e.getMessage());
    }
  }

}
