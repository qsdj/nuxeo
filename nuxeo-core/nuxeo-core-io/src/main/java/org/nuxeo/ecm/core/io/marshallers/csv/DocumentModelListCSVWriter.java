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
package org.nuxeo.ecm.core.io.marshallers.csv;

import static org.nuxeo.ecm.core.io.marshallers.csv.DocumentModelCSVWriter.SCHEMAS_CTX_DATA;
import static org.nuxeo.ecm.core.io.marshallers.csv.DocumentModelCSVWriter.XPATHS_CTX_DATA;
import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.csv.CSVPrinter;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.io.registry.Writer;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;

/**
 * @since 10.3
 */
@Setup(mode = SINGLETON, priority = REFERENCE)
public class DocumentModelListCSVWriter extends AbstractCSVWriter<List<DocumentModel>> {

    public DocumentModelListCSVWriter() {
        super();
    }

    @Override
    protected void write(List<DocumentModel> entity, CSVPrinter printer) throws IOException {
        Writer<DocumentModel> writer = registry.getWriter(ctx, DocumentModel.class, TEXT_CSV_TYPE);
        for (DocumentModel doc : entity) {
            writer.write(doc, DocumentModel.class, DocumentModel.class, TEXT_CSV_TYPE,
                    new OutputStreamWithCSVWriter(printer));
            printer.println();
        }
    }

    @Override
    protected void writeHeader(List<DocumentModel> entity, CSVPrinter printer) throws IOException {
        DocumentModelCSVHeader.printSystemPropertiesHeader(printer);
        List<String> schemas = ctx.getParameter(SCHEMAS_CTX_DATA) != null ? ctx.getParameter(SCHEMAS_CTX_DATA)
                : Arrays.asList(entity.get(0).getSchemas());
        List<String> xpaths = ctx.getParameter(XPATHS_CTX_DATA);
        DocumentModelCSVHeader.printPropertiesHeader(schemas, xpaths, printer);
        printer.println();
    }

}
