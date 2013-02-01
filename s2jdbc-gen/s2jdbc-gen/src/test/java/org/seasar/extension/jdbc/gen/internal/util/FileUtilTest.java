/*
 * Copyright 2004-2013 the Seasar Foundation and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.seasar.extension.jdbc.gen.internal.util;

import java.io.File;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author taedium
 * 
 */
public class FileUtilTest {

    /**
     * 
     */
    @Test
    public void testCreateJavaFile() {
        File baseDir = new File("base");
        File javaFile = new File(baseDir, "aaa");
        javaFile = new File(javaFile, "bbb");
        javaFile = new File(javaFile, "Ccc.java");
        assertEquals(javaFile, FileUtil.createJavaFile(baseDir, "aaa.bbb",
                "Ccc"));
    }

    /**
     * 
     */
    @Test
    public void testCreateJavaFile_defaultPackage() {
        File baseDir = new File("base");
        assertEquals(new File(baseDir, "Ccc.java"), FileUtil.createJavaFile(
                baseDir, null, "Ccc"));
    }
}
