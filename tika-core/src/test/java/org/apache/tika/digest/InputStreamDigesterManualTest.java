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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.Security;
import java.util.Base64;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.parser.ParseContext;

public class InputStreamDigesterManualTest {

    private static final byte[] CONTENT = "hello world".getBytes(StandardCharsets.UTF_8);
    private static final String KEY = "tk:digest:SHA256";

    private static String expectedDigest() throws Exception {
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(CONTENT));
    }

    private static InputStreamDigester newDigester(String key) {
        return new InputStreamDigester("SHA-256", key,
                bytes -> Base64.getEncoder().encodeToString(bytes));
    }

    @Test
    public void testDigestStoresTheContentLength() throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(CONTENT)) {
            newDigester(KEY).digest(tis, metadata, new ParseContext());
        }
        assertEquals("11", metadata.get(HttpHeaders.CONTENT_LENGTH));
        assertEquals(expectedDigest(), metadata.get(KEY));
    }

    @Test
    public void testDigestKeepsAnExistingContentLength() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_LENGTH, "999");
        try (TikaInputStream tis = TikaInputStream.get(CONTENT)) {
            newDigester(KEY).digest(tis, metadata, new ParseContext());
        }
        assertEquals("999", metadata.get(HttpHeaders.CONTENT_LENGTH));
    }

    @Test
    public void testDigestAcceptsANullParseContext() throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(CONTENT)) {
            newDigester(KEY).digest(tis, metadata, null);
        }
        assertEquals(expectedDigest(), metadata.get(KEY));
    }

    @Test
    public void testStreamCanBeReadAgainAfterTheDigest() throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(CONTENT)) {
            newDigester(KEY).digest(tis, metadata, new ParseContext());
            assertArrayEquals(CONTENT, IOUtils.toByteArray(tis));
        }
    }

    @Test
    public void testKeyOutsideTheDigestNamespaceIsStoredUnderItsOwnName() throws Exception {
        String key = "custom:digest:sha256";
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(CONTENT)) {
            newDigester(key).digest(tis, metadata, new ParseContext());
        }
        assertEquals(expectedDigest(), metadata.get(key));
    }

    @Test
    public void testDigestUsesTheProviderOfASubclass() throws Exception {
        InputStreamDigester digester = new InputStreamDigester("SHA-256", KEY,
                bytes -> Base64.getEncoder().encodeToString(bytes)) {
            @Override
            protected Provider getProvider() {
                return Security.getProvider("SUN");
            }
        };
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(CONTENT)) {
            digester.digest(tis, metadata, new ParseContext());
        }
        assertEquals(expectedDigest(), metadata.get(KEY));
    }

    @Test
    public void testDigestPropertyIsRegisteredForBothKeyShapes() throws Exception {
        String custom = "custom:digest:sha256";
        newDigester(custom);
        newDigester(KEY);
        assertNotNull(Property.get(custom));
        assertNotNull(Property.get(KEY));
        assertEquals(custom, Property.get(custom).getName());
        assertEquals(KEY, Property.get(KEY).getName());
    }
}
