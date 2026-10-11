/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.digest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

public class DigestHelperManualTest {

    private static final String MD5_KEY = "tk:digest:MD5";
    private static final Encoder HEX = bytes -> HexFormat.of().formatHex(bytes);
    private static final byte[] SOURCE = "hello, digest helper".getBytes(StandardCharsets.US_ASCII);

    private static ParseContext contextWith(Digester digester, boolean skipContainer) {
        ParseContext context = new ParseContext();
        context.set(DigesterFactory.class, new DigesterFactory() {
            @Override
            public Digester build() {
                return digester;
            }

            @Override
            public boolean isSkipContainerDocumentDigest() {
                return skipContainer;
            }
        });
        return context;
    }

    private static InputStreamDigester md5Digester() {
        return new InputStreamDigester("MD5", MD5_KEY, HEX);
    }

    private static String md5Of(byte[] bytes) throws IOException {
        Metadata m = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            md5Digester().digest(tis, m, new ParseContext());
        }
        return m.get(MD5_KEY);
    }

    @Test
    public void testSkipContainerWithoutDepthPublishesNoDigest() throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(SOURCE, new Metadata())) {
            DigestHelper.maybeDigest(tis, metadata, contextWith(md5Digester(), true));
        }
        assertNull(metadata.get(MD5_KEY));
    }

    @Test
    public void testSkipContainerAtDepthZeroPublishesNoDigest() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.EMBEDDED_DEPTH, 0);
        try (TikaInputStream tis = TikaInputStream.get(SOURCE, new Metadata())) {
            DigestHelper.maybeDigest(tis, metadata, contextWith(md5Digester(), true));
        }
        assertNull(metadata.get(MD5_KEY));
    }

    @Test
    public void testSkipContainerStillDigestsEmbeddedDocuments() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.EMBEDDED_DEPTH, 1);
        try (TikaInputStream tis = TikaInputStream.get(SOURCE, new Metadata())) {
            DigestHelper.maybeDigest(tis, metadata, contextWith(md5Digester(), true));
        }
        assertEquals(md5Of(SOURCE), metadata.get(MD5_KEY));
    }

    @Test
    public void testCloseFailureIsSuppressedUnderTheTranslationFailure() throws Exception {
        InputStreamDigester failingClose = new InputStreamDigester("MD5", MD5_KEY, HEX) {
            @Override
            public DigestSink digestSink(Metadata metadata, ParseContext parseContext) {
                return new DigestSink() {
                    @Override
                    public void write(int b) throws IOException {
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                    }

                    @Override
                    protected void finish(boolean publish) {
                        throw new IllegalStateException("close failed");
                    }
                };
            }
        };
        Metadata metadata = new Metadata();
        metadata.set(FailingTestTranslator.MODE, "fail");
        try (TikaInputStream tis = TikaInputStream.get(SOURCE, new Metadata())) {
            IOException e = assertThrows(IOException.class,
                    () -> DigestHelper.maybeDigest(tis, metadata, contextWith(failingClose, false)));
            assertEquals("translator gave up half way", e.getMessage());
            assertEquals(1, e.getSuppressed().length);
            assertEquals("close failed", e.getSuppressed()[0].getMessage());
        }
    }
}
