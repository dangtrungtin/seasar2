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
package org.seasar.framework.jpa.exception;

import javax.persistence.EntityManager;

import org.seasar.framework.exception.SRuntimeException;
import org.seasar.framework.jpa.Dialect;

/**
 * {@link Dialect}が存在しない場合にスローされる例外です。
 * 
 * @author koichik
 */
public class DialectNotFoundException extends SRuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * インスタンスを構築します。
     * 
     * @param em
     *            エンティティマネージャ
     */
    public DialectNotFoundException(final EntityManager em) {
        super("ESSR0089", new Object[] { em });
    }

}
