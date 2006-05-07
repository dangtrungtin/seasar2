/*
 * Copyright 2004-2006 the Seasar Foundation and the Others.
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
package org.seasar.framework.ejb.unit;

import java.util.List;

import org.seasar.framework.ejb.unit.impl.DefaultProxiedObjectResolver;
import org.seasar.framework.ejb.unit.impl.EntityListIntrospector;


/**
 * @author taedium
 * 
 */
public class EntityListReader extends EntityReader {

    public EntityListReader(List<?> entities) {
        this(entities, DefaultProxiedObjectResolver.INSTANCE);
    }
    
    public EntityListReader(List<?> entities, ProxiedObjectResolver resolver) {
        super(new EntityListIntrospector(entities, resolver), resolver); 

        for (PersistentClassDesc classDesc : getEntityIntrospector().getAllPersistentClassDescs()) {
            setupColumns(classDesc);
        }
        for (Object entity : entities) {
            if (entity == null) {
                continue;
            }
            PersistentClassDesc classDesc = getEntityIntrospector().getPersistentClassDesc(entity);
            startSetupRows(classDesc, entity);
            releaseProcessedEntity(entity);
        }
    }
}
