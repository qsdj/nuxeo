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
package org.nuxeo.ecm.platform.csv.export;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.bulk.message.BulkStatus.State.COMPLETED;
import static org.nuxeo.ecm.core.io.marshallers.csv.DocumentModelCSVHeader.SYSTEM_PROPERTIES_HEADER_FIELDS;
import static org.nuxeo.ecm.core.test.DocumentSetRepositoryInit.DOC_BY_LEVEL;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.ZipUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.platform.csv.export.action.CSVExportAction;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.core.io.download.DownloadService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.DocumentSetRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

@RunWith(FeaturesRunner.class)
@Features({ CoreBulkFeature.class, CoreFeature.class })
@Deploy("org.nuxeo.ecm.platform.csv.export")
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
@RepositoryConfig(init = DocumentSetRepositoryInit.class)
@Ignore("NXP-26039")
public class TestCSVExportAction {

    @Inject
    public CoreSession session;

    @Inject
    public BulkService bulkService;

    @Inject
    public DownloadService downloadService;

    protected static abstract class DummyServletOutputStream extends ServletOutputStream {
        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
        }
    }

    @Test
    public void testSimple() throws Exception {
        BulkCommand command = createCommand();
        testCsvExport(command);
    }

    @Test
    public void testSimpleWithMultipleBuckets() throws Exception {
        BulkCommand command = createCommand();
        command.setBucketSize(1);
        command.setBatchSize(1);
        testCsvExport(command);
    }

    public void testCsvExport(BulkCommand command) throws Exception {
        bulkService.submit(command);
        assertTrue("Bulk action didn't finish", bulkService.await(command.getId(), Duration.ofSeconds(60)));

        BulkStatus status = bulkService.getStatus(command.getId());
        assertEquals(COMPLETED, status.getState());
        assertEquals(DOC_BY_LEVEL, status.getProcessed());
        assertEquals(DOC_BY_LEVEL, status.getTotal());

        String url = Framework.getService(DownloadService.class).getDownloadUrl(command.getId());
        assertEquals(url, status.getResult().get("url"));

        Blob blob = getBlob(command.getId());
        // file is ziped
        assertNotNull(blob);
        assertEquals("zip", FilenameUtils.getExtension(blob.getFilename()));
        try (InputStream is = new FileInputStream(blob.getFile())) {
            assertTrue(ZipUtils.isValid(is));
        }

        // file has the correct number of lines
        File file = getUnzipFile(command, blob);

        List<String> lines = Files.lines(file.toPath()).collect(Collectors.toList());
        // number of docs plus the header
        assertEquals(DOC_BY_LEVEL + 1, lines.size());

        // Check header
        assertArrayEquals(SYSTEM_PROPERTIES_HEADER_FIELDS, lines.get(0).split(","));
        long count = lines.stream().filter(Predicate.isEqual(lines.get(0))).count();
        assertEquals(1, count);

        // file is sorted
        List<String> content = lines.subList(1, lines.size());
        List<String> sortedContent = new ArrayList<>(content);
        Collections.sort(sortedContent);
        assertEquals(content, sortedContent);
    }

    @Test
    public void testExportWithParams() throws Exception {
        BulkCommand command = createCommandWithParams();
        bulkService.submit(command);
        assertTrue("Bulk action didn't finish", bulkService.await(command.getId(), Duration.ofSeconds(60)));

        BulkStatus status = bulkService.getStatus(command.getId());
        assertEquals(COMPLETED, status.getState());
        assertEquals(DOC_BY_LEVEL, status.getProcessed());
        assertEquals(DOC_BY_LEVEL, status.getTotal());

        Blob blob = getBlob(command.getId());
        // file is ziped
        assertNotNull(blob);
        try (InputStream is = new FileInputStream(blob.getFile())) {
            assertTrue(ZipUtils.isValid(is));
        }

        // file has the correct number of lines
        File file = getUnzipFile(command, blob);

        List<String> lines = Files.lines(file.toPath()).collect(Collectors.toList());
        // Check header
        List<String> header = Arrays.asList(lines.get(0).split(","));
        assertArrayEquals(
                new String[] { "dc:contributors", "dc:coverage", "dc:created", "dc:creator", "dc:description",
                        "dc:expired", "dc:format", "dc:issued", "dc:language", "dc:lastContributor", "dc:modified",
                        "dc:nature", "dc:publisher", "dc:rights", "dc:source", "dc:subjects", "dc:title", "dc:valid",
                        "cpx:complex/foo" },
                header.subList(18, 37).toArray());

    }

    protected File getUnzipFile(BulkCommand command, Blob blob) throws IOException {
        Path dir = Files.createTempDirectory(CSVExportAction.ACTION_NAME + "test" + System.currentTimeMillis());
        ZipUtils.unzip(blob.getFile(), dir.toFile());
        return new File(dir.toFile(), command.getId() + ".csv");
    }

    @Test
    public void testMulti() throws Exception {
        BulkCommand command1 = createCommand();
        BulkCommand command2 = createCommand();
        bulkService.submit(command1);
        bulkService.submit(command2);

        assertTrue("Bulk action didn't finish", bulkService.await(Duration.ofSeconds(60)));

        BulkStatus status = bulkService.getStatus(command1.getId());
        assertEquals(COMPLETED, status.getState());
        assertEquals(DOC_BY_LEVEL, status.getProcessed());

        status = bulkService.getStatus(command2.getId());
        assertEquals(COMPLETED, status.getState());
        assertEquals(DOC_BY_LEVEL, status.getProcessed());

        Blob blob1 = getBlob(command1.getId());
        Blob blob2 = getBlob(command2.getId());

        // this produce the exact same content
        HashCode hash1 = hash(getUnzipFile(command1, blob1));
        HashCode hash2 = hash(getUnzipFile(command2, blob2));
        assertEquals(hash1, hash2);
    }

    @Test
    public void testDownloadCSV() throws Exception {

        BulkCommand command = createCommand();
        bulkService.submit(command);
        assertTrue("Bulk action didn't finish", bulkService.await(command.getId(), Duration.ofSeconds(60)));

        BulkStatus status = bulkService.getStatus(command.getId());
        assertEquals(COMPLETED, status.getState());
        assertEquals(DOC_BY_LEVEL, status.getProcessed());
        assertEquals(DOC_BY_LEVEL, status.getTotal());

        Path dir = Files.createTempDirectory(CSVExportAction.ACTION_NAME + "test" + System.currentTimeMillis());
        File testZip = new File(dir.toFile(), "test.zip");
        try (FileOutputStream out = new FileOutputStream(testZip)) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getMethod()).thenReturn("GET");

            HttpServletResponse response = mock(HttpServletResponse.class);
            ServletOutputStream sos = new DummyServletOutputStream() {
                @Override
                public void write(int b) throws IOException {
                    out.write(b);
                }
            };
            PrintWriter printWriter = new PrintWriter(sos);
            when(response.getOutputStream()).thenReturn(sos);
            when(response.getWriter()).thenReturn(printWriter);

            String url = (String) status.getResult().get("url");
            downloadService.handleDownload(request, response, null, url);
        }

        ZipUtils.unzip(testZip, dir.toFile());
        File csv = new File(dir.toFile(), command.getId() + ".csv");
        List<String> lines = Files.lines(csv.toPath()).collect(Collectors.toList());
        // number of docs plus header
        assertEquals(DOC_BY_LEVEL + 1, lines.size());

    }

    private HashCode hash(File file) throws IOException {
        return com.google.common.io.Files.asByteSource(file).hash(Hashing.sha256());
    }

    protected BulkCommand createCommand() {
        DocumentModel model = session.getDocument(new PathRef("/default-domain/workspaces/test"));
        String nxql = String.format("SELECT * from ComplexDoc where ecm:parentId='%s'", model.getId());
        return new BulkCommand.Builder(CSVExportAction.ACTION_NAME, nxql).repository(session.getRepositoryName())
                                                                         .user(session.getPrincipal().getName())
                                                                         .build();
    }

    protected BulkCommand createCommandWithParams() {
        DocumentModel model = session.getDocument(new PathRef("/default-domain/workspaces/test"));
        String nxql = String.format("SELECT * from ComplexDoc where ecm:parentId='%s'", model.getId());
        return new BulkCommand.Builder(CSVExportAction.ACTION_NAME, nxql).repository(session.getRepositoryName())
                                                                         .user(session.getPrincipal().getName())
                                                                         .param("schemas",
                                                                                 ImmutableList.of("dublincore"))
                                                                         .param("xpaths",
                                                                                 ImmutableList.of("cpx:complex/foo"))
                                                                         .build();
    }

    protected Blob getBlob(String commandId) {
        // the convention is that the blob is created in the download storage with the command id
        TransientStore download = Framework.getService(TransientStoreService.class)
                                           .getStore(DownloadService.TRANSIENT_STORE_STORE_NAME);

        List<Blob> blobs = download.getBlobs(commandId);
        assertNotNull(blobs);
        assertEquals(1, blobs.size());
        return blobs.get(0);
    }
}
