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

import org.junit.jupiter.api.Test;

public class CharsetUtilsGeneratedTest {

    @Test
    public void testCleanWithValidCharset() {
        assertEquals("UTF-8", CharsetUtils.clean("UTF-8"));
    }

    @Test
    public void testCleanWithInvalidCharset() {
        assertEquals(null, CharsetUtils.clean("invalid-charset"));
    }

    @Test
    public void testCleanWithISOCharset() {
        assertEquals("ISO-8859-1", CharsetUtils.clean("ISO-8859-1"));
    }

    @Test
    public void testCleanWithCPCharset() {
        assertEquals("windows-1252", CharsetUtils.clean("cp-1252"));
    }

    @Test
    public void testCleanWithWINCharset() {
        assertEquals("windows-1252", CharsetUtils.clean("windows-1252"));
    }

    @Test
    public void testCleanWithUnknownCharsetName() {
        assertEquals(null, CharsetUtils.clean("icu-charset"));
    }
}
