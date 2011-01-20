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

import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.authorizable.User;

import java.util.HashMap;

@RunWith(MockitoJUnitRunner.class)
public class SakaiUserProcessorTest {
  private SakaiUserProcessor sakaiUserProcessor;
  @Mock
  private Session session;
  @Mock
  private User user;

  @Before
  public void setUp() {
    sakaiUserProcessor = new SakaiUserProcessor();
  }

  @Test
  public void nilTest() throws Exception {
    // Does nothing except verify that an exception isn't thrown.
    sakaiUserProcessor.process(user, session, new Modification(ModificationType.CREATE, "", ""), new HashMap<String, Object[]>());
  }

}
