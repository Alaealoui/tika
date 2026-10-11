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
package org.apache.tika.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CharsetUtilsIcuTest {

    private static final String ICU_ONLY_NAME = "icu-only-charset";

    private Method originalGetCharsetIcu;
    private Method originalIsSupportedIcu;

    @BeforeEach
    public void installIcuStub() throws Exception {
        originalGetCharsetIcu = readHook("getCharsetICU");
        originalIsSupportedIcu = readHook("isSupportedICU");
        FakeIcu.supported = false;
        FakeIcu.failure = null;
        writeHook("getCharsetICU", FakeIcu.class.getMethod("forNameICU", String.class));
        writeHook("isSupportedICU", FakeIcu.class.getMethod("isSupported", String.class));
    }

    @AfterEach
    public void restoreIcuHooks() throws Exception {
        writeHook("getCharsetICU", originalGetCharsetIcu);
        writeHook("isSupportedICU", originalIsSupportedIcu);
    }

    @Test
    public void testIsSupportedTrustsIcuWhenItAcceptsTheName() {
        FakeIcu.supported = true;
        assertTrue(CharsetUtils.isSupported(ICU_ONLY_NAME));
    }

    @Test
    public void testIsSupportedFallsBackToJdkWhenIcuRejectsTheName() {
        FakeIcu.supported = false;
        assertFalse(CharsetUtils.isSupported(ICU_ONLY_NAME));
        assertTrue(CharsetUtils.isSupported("UTF-8"));
    }

    @Test
    public void testIsSupportedReturnsFalseWhenIcuThrows() {
        FakeIcu.failure = new IllegalStateException("icu failure");
        assertFalse(CharsetUtils.isSupported(ICU_ONLY_NAME));
    }

    @Test
    public void testForNameUsesIcuCharsetWhenJdkDoesNotKnowTheName() {
        Charset charset = CharsetUtils.forName(ICU_ONLY_NAME);
        assertEquals(StandardCharsets.UTF_16LE, charset);
    }

    @Test
    public void testCleanReturnsTheNameOfTheIcuCharset() {
        assertEquals("UTF-16LE", CharsetUtils.clean(ICU_ONLY_NAME));
    }

    private static Method readHook(String fieldName) throws Exception {
        Field field = CharsetUtils.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Method) field.get(null);
    }

    private static void writeHook(String fieldName, Method value) throws Exception {
        Field field = CharsetUtils.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    public static class FakeIcu {

        static boolean supported;
        static RuntimeException failure;

        public static boolean isSupported(String name) {
            if (failure != null) {
                throw failure;
            }
            return supported;
        }

        public static Charset forNameICU(String name) {
            return StandardCharsets.UTF_16LE;
        }
    }
}
