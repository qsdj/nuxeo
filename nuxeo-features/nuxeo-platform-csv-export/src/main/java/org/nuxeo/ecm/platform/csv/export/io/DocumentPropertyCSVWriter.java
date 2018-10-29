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
package org.nuxeo.ecm.platform.csv.export.io;

import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;
import static org.nuxeo.ecm.platform.csv.export.io.DocumentModelCSVHeader.VOCABULARY_TYPES;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVPrinter;
import org.nuxeo.common.utils.i18n.I18NUtils;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.impl.ArrayProperty;
import org.nuxeo.ecm.core.io.marshallers.csv.AbstractCSVWriter;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.primitives.BinaryType;
import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolver;
import org.nuxeo.ecm.directory.Directory;
import org.nuxeo.ecm.directory.DirectoryEntryResolver;
import org.nuxeo.ecm.directory.Session;

/**
 * @since 10.3
 */
@Setup(mode = SINGLETON, priority = REFERENCE)
public class DocumentPropertyCSVWriter extends AbstractCSVWriter<Property> {

    public static final String LIST_DELIMITER = "\n";

    public static final String LANG_CTX_DATA = "lang";

    public DocumentPropertyCSVWriter() {
        super();
    }

    @Override
    protected void write(Property entity, CSVPrinter printer) throws IOException {
        if (entity.isScalar()) {
            writeScalarProperty(entity, printer);
        } else if (entity.isList()) {
            writeListProperty(entity, printer);
        } else {
            writeUnsupported(entity.getType(), printer);
        }
    }

    @Override
    protected void writeHeader(Property entity, CSVPrinter printer) throws IOException {
        printer.printRecord(entity.getXPath());
    }

    protected void writeScalarProperty(Property entity, CSVPrinter printer) throws IOException {
        Object value = entity.getValue();
        Type type = entity.getType();
        if (type instanceof BinaryType) {
            writeUnsupported(type, printer);
        } else {
            String valueAsString = null;
            if (value == null) {
                printer.print(null);
            } else {
                valueAsString = type.encode(value);
                printer.print(valueAsString);
            }
            ObjectResolver resolver = entity.getType().getObjectResolver();
            if (resolver instanceof DirectoryEntryResolver) {
                DirectoryEntryResolver directoryEntryResolver = (DirectoryEntryResolver) resolver;
                Directory directory = directoryEntryResolver.getDirectory();
                if (VOCABULARY_TYPES.contains(directory.getSchema())) {
                    if (value == null) {
                        printer.print(null);
                    } else {
                        try (Session session = directory.getSession()) {
                            String lang = ctx.getParameter(LANG_CTX_DATA);
                            String label = (String) session.getEntry(valueAsString).getProperty(directory.getSchema(),
                                    "label");
                            Locale locale = lang != null ? Locale.forLanguageTag(lang) : ctx.getLocale();
                            String translated = I18NUtils.getMessageString("messages", label, new Object[0], locale);
                            printer.print(translated);
                        }
                    }
                }
            }
        }
    }

    protected void writeListProperty(Property entity, CSVPrinter printer) throws IOException {
        ListType type = (ListType) entity.getType();
        if (entity instanceof ArrayProperty) {
            Object[] array = (Object[]) entity.getValue();
            if (array == null) {
                printer.print(null);
                return;
            }
            Type itemType = type.getFieldType();
            if (itemType instanceof BinaryType) {
                writeUnsupported(type, printer);
            } else {
                String value = Arrays.stream(array).map(itemType::encode).collect(Collectors.joining(LIST_DELIMITER));
                printer.print(value);
                ObjectResolver resolver = itemType.getObjectResolver();
                if (resolver instanceof DirectoryEntryResolver) {
                    List<String> translated = new ArrayList<>();
                    DirectoryEntryResolver directoryEntryResolver = (DirectoryEntryResolver) resolver;
                    Directory directory = directoryEntryResolver.getDirectory();
                    if (VOCABULARY_TYPES.contains(directory.getSchema())) {
                        try (Session session = directory.getSession()) {
                            String lang = ctx.getParameter(LANG_CTX_DATA);
                            for (Object obj : array) {
                                String label = (String) session.getEntry(itemType.encode(obj))
                                                               .getProperty(directory.getSchema(), "label");
                                Locale locale = lang != null ? Locale.forLanguageTag(lang) : ctx.getLocale();
                                translated.add(I18NUtils.getMessageString("messages", label, new Object[0], locale));
                            }
                        }
                    }
                    printer.print(translated.stream().collect(Collectors.joining(LIST_DELIMITER)));
                }
            }
        } else {
            writeUnsupported(type, printer);
        }
    }

    protected void writeUnsupported(Type type, CSVPrinter printer) throws IOException {
        printer.print(String.format("type %s is not supported", type.getName()));
    }

}
