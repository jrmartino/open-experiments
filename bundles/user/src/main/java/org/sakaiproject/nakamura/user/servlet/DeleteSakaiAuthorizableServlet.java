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
package org.sakaiproject.nakamura.user.servlet;

import edu.emory.mathcs.backport.java.util.Arrays;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceNotFoundException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceExtension;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceParameter;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.api.doc.ServiceSelector;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.user.AuthorizablePostProcessService;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.util.osgi.EventUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import javax.servlet.http.HttpServletResponse;

/**
 * Sling Post Operation implementation for deleting one or more users and/or groups from the
 * Sparse AuthorizableManager.
 * TODO Update comments for Sparse.
 *
 * <h2>Rest Service Description</h2>
 * <p>
 * Deletes an Authorizable, currently a user or a group. Maps on to nodes of resourceType <code>sling/users</code> or <code>sling/users</code> like
 * <code>/rep:system/rep:userManager/rep:users</code> or <code>/rep:system/rep:userManager/rep:groups</code> mapped to a resource url
 * <code>/system/userManager/user</code> or <code>/system/userManager/group</code>. This servlet responds at
 * <code>/system/userManager/user.delete.html</code> or <code>/system/userManager/group.delete.html</code>.
 * The servlet also responds to single delete requests eg <code>/system/userManager/group/newGroup.delete.html</code>
 * </p>
 * <h4>Methods</h4>
 * <ul>
 * <li>POST</li>
 * </ul>
 * <h4>Post Parameters</h4>
 * <dl>
 * <dt>:applyTo</dt>
 * <dd>An array of relative resource references to Authorizables to be deleted, if this parameter is present, the url is ignored and all the Authorizables in the list are removed.</dd>
 * </dl>
 * <h4>Response</h4>
 * <dl>
 * <dt>200</dt>
 * <dd>Success, no body.</dd>
 * <dt>404</dt>
 * <dd>The resource was not found</dd>
 * <dt>500</dt>
 * <dd>Failure</dd>
 * </dl>
 * <h4>Example</h4>
 *
 * <code>
 * curl -Fgo=1 http://localhost:8080/system/userManager/user/ieb.delete.html
 * </code>
 *
 *
 * @scr.component metatype="no" immediate="true"
 * @scr.service interface="javax.servlet.Servlet"
 * @scr.property name="sling.servlet.resourceTypes" values.0="sling/user" values.1="sling/group" values.2="sling/userManager"
 * @scr.property name="sling.servlet.methods" value="POST"
 * @scr.property name="sling.servlet.selectors" value="delete"
 *
 *
 */
@ServiceDocumentation(name="Delete Authorizable (Group and User) Servlet",
    description="Deletes a group. Maps on to nodes of resourceType sling/groups like " +
    		"/rep:system/rep:userManager/rep:groups mapped to a resource url " +
    		"/system/userManager/group. This servlet responds at " +
    		"/system/userManager/group.delete.html. The servlet also responds to single delete " +
    		"requests eg /system/userManager/group/g-groupname.delete.html",
    shortDescription="Delete a group or user",
    bindings=@ServiceBinding(type=BindingType.TYPE,bindings={"sling/group", "sling/user"},
        selectors=@ServiceSelector(name="delete", description="Deletes one or more authorizables (groups or users)"),
        extensions=@ServiceExtension(name="html", description="Posts produce html containing the update status")),
    methods=@ServiceMethod(name="POST",
        description={"Delete a group or user, or set of groups.",
            "Example<br>" +
            "<pre>curl -Fgo=1 http://localhost:8080/system/userManager/group/g-groupname.delete.html</pre>"},
        parameters={
        @ServiceParameter(name=":applyTo", description="An array of relative resource references to groups to be deleted, if this parameter is present, the url is ignored and all listed groups are removed.")
    },
    response={
    @ServiceResponse(code=200,description="Success, a redirect is sent to the group's resource locator with HTML describing status."),
    @ServiceResponse(code=404,description="Group or User was not found."),
    @ServiceResponse(code=500,description="Failure with HTML explanation.")
        }))
public class DeleteSakaiAuthorizableServlet extends AbstractAuthorizablePostServlet {

  /**
   *
   */
  private static final long serialVersionUID = 3417673949322305891L;


  private static final Logger LOGGER = LoggerFactory.getLogger(DeleteSakaiAuthorizableServlet.class);


  /**
   * @scr.reference
   */
  protected transient AuthorizablePostProcessService postProcessorService;

  /**
   * @scr.reference
   */
  protected transient EventAdmin eventAdmin;

  @SuppressWarnings("unchecked")
  @Override
  protected void handleOperation(SlingHttpServletRequest request, HtmlResponse response,
      List<Modification> changes) {
    Session session = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(javax.jcr.Session.class));
    try {
      AuthorizableManager authorizableManager = session.getAuthorizableManager();
      boolean mustExist;
      Iterator<Resource> res = getApplyToResources(request);
      if (res == null) {
        mustExist = true;
        res = Arrays.asList(new Resource[] {request.getResource()}).iterator();
      } else {
        mustExist = false;
      }

      while (res.hasNext()) {
        Resource resource = res.next();
        Authorizable authorizable = resource.adaptTo(Authorizable.class);
        if (authorizable == null) {
          String msg = "Missing source " + resource.getPath() + " for delete";
          if (mustExist) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND, msg);
            throw new ResourceNotFoundException(msg);
          } else {
            // Move on to the next one.
            LOGGER.info(msg);
          }
        } else {
          // In the special case of deletion, the "post-processors" are treated as pre-processors.
          postProcessorService.process(authorizable, session, ModificationType.DELETE, request);

          // Remove Authorizable.
          authorizableManager.delete(authorizable.getId());
          changes.add(Modification.onDeleted(resource.getPath()));

          // Launch an OSGi event for each authorizable.
          try {
            Dictionary<String, String> properties = new Hashtable<String, String>();
            properties.put(UserConstants.EVENT_PROP_USERID, authorizable.getId());
            String topic;
            if (authorizable instanceof Group) {
              topic  = UserConstants.TOPIC_GROUP_DELETED;
            } else {
              topic = UserConstants.TOPIC_USER_DELETED;
            }
            EventUtils.sendOsgiEvent(properties, topic, eventAdmin);
          } catch (Exception e) {
            // Trap all exception so we don't disrupt the normal behaviour.
            LOGGER.error("Failed to launch an OSGi event for creating a user.", e);
          }

        }
      }
    } catch (Exception e) {
      LOGGER.warn(e.getMessage(),e);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
      return;
    }
  }

  /**
   * Returns an iterator on <code>Resource</code> instances addressed in the
   * {@link SlingPostConstants#RP_APPLY_TO} request parameter. If the request
   * parameter is not set, <code>null</code> is returned. If the parameter is
   * set with valid resources an empty iterator is returned. Any resources
   * addressed in the {@link SlingPostConstants#RP_APPLY_TO} parameter is
   * ignored.
   *
   * @param request The <code>SlingHttpServletRequest</code> object used to
   *            get the {@link SlingPostConstants#RP_APPLY_TO} parameter.
   * @return The iterator of resources listed in the parameter or
   *         <code>null</code> if the parameter is not set in the request.
   */
  protected Iterator<Resource> getApplyToResources(
          SlingHttpServletRequest request) {

      String[] applyTo = request.getParameterValues(SlingPostConstants.RP_APPLY_TO);
      if (applyTo == null) {
          return null;
      }

      return new ApplyToIterator(request, applyTo);
  }

  private static class ApplyToIterator implements Iterator<Resource> {

      private final ResourceResolver resolver;

      private final Resource baseResource;

      private final String[] paths;

      private int pathIndex;

      private Resource nextResource;

      ApplyToIterator(SlingHttpServletRequest request, String[] paths) {
          this.resolver = request.getResourceResolver();
          this.baseResource = request.getResource();
          this.paths = paths;
          this.pathIndex = 0;

          nextResource = seek();
      }

      public boolean hasNext() {
          return nextResource != null;
      }

      public Resource next() {
          if (!hasNext()) {
              throw new NoSuchElementException();
          }

          Resource result = nextResource;
          nextResource = seek();

          return result;
      }

      public void remove() {
          throw new UnsupportedOperationException();
      }

      private Resource seek() {
          while (pathIndex < paths.length) {
              String path = paths[pathIndex];
              pathIndex++;

              Resource res = resolver.getResource(baseResource, path);
              if (res != null) {
                  return res;
              }
          }

          // no more elements in the array
          return null;
      }
  }


}
