/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 *     pierre
 */
package org.nuxeo.ecm.core.bulk;

import static java.lang.Boolean.TRUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.core.api.trash.TrashService.DOCUMENT_TRASHED;
import static org.nuxeo.ecm.core.bulk.action.TrashAction.ACTION_NAME;
import static org.nuxeo.ecm.core.bulk.message.BulkStatus.State.COMPLETED;
import static org.nuxeo.ecm.core.test.DocumentSetRepositoryInit.created_non_proxy;
import static org.nuxeo.ecm.core.test.DocumentSetRepositoryInit.created_total;
import static org.nuxeo.ecm.core.trash.PropertyTrashService.SYSPROP_IS_TRASHED;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.EventListenerDescriptor;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.DocumentSetRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
@RepositoryConfig(init = DocumentSetRepositoryInit.class)
public class TestTrashAction {

    @Inject
    public BulkService service;

    @Inject
    public CoreSession session;

    @Inject
    public EventService eventService;

    @Inject
    public TransactionalFeature txFeature;

    private static class TestListener extends EventListenerDescriptor {

        private int count;

        private TestListener() {
            events = new HashSet<>(Collections.singleton(DOCUMENT_TRASHED));
        }

        @Override
        public void initListener() {
            inLineListener = event -> count++;
        }
    }

    @Test
    public void test() throws Exception {

        DocumentModel model = session.getDocument(new PathRef("/default-domain/workspaces/test"));
        String nxql = String.format("SELECT * from Document where ecm:ancestorId='%s'", model.getId());

        TestListener listener = new TestListener();

        eventService.addEventListener(listener);

        String commandId = service.submit(
                new BulkCommand.Builder(ACTION_NAME, nxql).repository(session.getRepositoryName())
                                                          .user(session.getPrincipal().getName())
                                                          .param("value", Boolean.TRUE)
                                                          .build());

        assertTrue("Bulk action didn't finish", service.await(Duration.ofSeconds(600)));

        BulkStatus status = service.getStatus(commandId);
        assertNotNull(status);
        assertEquals(COMPLETED, status.getState());
        assertEquals(created_total, status.getProcessed());

        txFeature.nextTransaction();

        for (DocumentModel descendant : session.query(nxql)) {
            assertEquals(TRUE, session.getDocumentSystemProp(descendant.getRef(), SYSPROP_IS_TRASHED, Boolean.class));
        }
        assertEquals(created_non_proxy, listener.count);

        eventService.removeEventListener(listener);
    }

}
