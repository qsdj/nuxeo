/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     mhilaire
 *     Funsho David
 *
 */

package org.nuxeo.directory.test;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


@RunWith(FeaturesRunner.class)
@Features({PlatformFeature.class})
@Deploy({"org.nuxeo.ecm.directory.tests:test-directories-bundle-custom-user-schema.xml"})
public class TestDirectoryCustomUserAndRelations {

    @Inject
    protected CoreSession sess;

    @Inject
    protected UserManager userManager;

    // Default admin tests
    @Test
    public void adminCanCreateEntry() throws Exception {
        // Given the admin user
        Map<String, Object> map = new HashMap<>();
        map.put("username", "user_0");
        map.put("password", "pass_0");
        map.put("groups", Arrays.asList("members", "administrators"));
        map.put("whateverProperty", Arrays.asList("members", "administrators"));

        // Create a bareuser
        DocumentModel entry = userManager.getBareUserModel();
        entry.setProperties("user", map);
        DocumentModel entry2 = userManager.createUser(entry);

        // I have created an entry
        DocumentModel entry3 = sess.getDocument(new IdRef(entry2.getId()));

        Object whateverProperty = entry3.getProperty("user:whateverProperty");
        Assert.assertNotNull(whateverProperty);

        Assert.assertNotNull(entry3);

    }
}
