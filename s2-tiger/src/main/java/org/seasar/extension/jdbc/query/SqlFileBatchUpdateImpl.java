/*
 * Copyright 2004-2007 the Seasar Foundation and the Others.
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
package org.seasar.extension.jdbc.query;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

import org.seasar.extension.jdbc.JdbcContext;
import org.seasar.extension.jdbc.JdbcManager;
import org.seasar.extension.jdbc.SqlFileBatchUpdate;
import org.seasar.extension.jdbc.exception.IllegalParamTypeRuntimeException;
import org.seasar.extension.jdbc.types.ValueTypes;
import org.seasar.extension.sql.Node;
import org.seasar.extension.sql.SqlContext;
import org.seasar.extension.sql.cache.NodeCache;
import org.seasar.extension.sql.context.SqlContextImpl;
import org.seasar.framework.beans.BeanDesc;
import org.seasar.framework.beans.PropertyDesc;
import org.seasar.framework.beans.factory.BeanDescFactory;
import org.seasar.framework.exception.ResourceNotFoundRuntimeException;
import org.seasar.framework.util.PreparedStatementUtil;
import org.seasar.framework.util.StatementUtil;

/**
 * {@link SqlFileBatchUpdate}の実装クラスです。
 * 
 * @author taedium
 * 
 */
public class SqlFileBatchUpdateImpl extends AbstractQuery<SqlFileBatchUpdate>
        implements SqlFileBatchUpdate {

    /**
     * SQLファイルのパスです。
     */
    protected String path;

    /**
     * SQLの解析ノードです。
     */
    protected Node node;

    /**
     * SQLコンテキストです。
     */
    protected SqlContext sqlContext;

    /**
     * パラメータのクラスです。
     */
    protected Class<?> paramClass;

    /**
     * パラメータの配列のリストです。
     */
    protected List<Object> paramsList = new ArrayList<Object>();

    /**
     * {@link SqlFileBatchUpdate}を作成します。
     * 
     * @param jdbcManager
     *            JDBCマネージャ
     * @param path
     *            SQLファイルのパス
     */
    public SqlFileBatchUpdateImpl(JdbcManager jdbcManager, String path) {
        this(jdbcManager, path, null);
    }

    /**
     * {@link SqlFileBatchUpdate}を作成します。
     * 
     * @param jdbcManager
     *            JDBCマネージャ
     * @param path
     *            SQLファイルのパス
     * @param paramClass
     *            パラメータのクラス
     */
    public SqlFileBatchUpdateImpl(JdbcManager jdbcManager, String path,
            Class<?> paramClass) {
        super(jdbcManager);
        if (path == null) {
            throw new NullPointerException("path");
        }
        this.path = path;
        this.paramClass = paramClass;
    }

    public int[] executeBatch() {
        prepare("executeBatch");
        int[] ret = null;
        JdbcContext jdbcContext = jdbcManager.getJdbcContext();
        try {
            PreparedStatement ps = getPreparedStatement(jdbcContext);
            for (int i = 0; i < paramsList.size(); ++i) {
                Object param = paramsList.get(i);
                prepareParameter(param);
                prepareSql();
                logSql();
                prepareInParams(ps);
                PreparedStatementUtil.addBatch(ps);
                resetParams();
            }
            ret = PreparedStatementUtil.executeBatch(ps);
        } finally {
            if (!jdbcContext.isTransactional()) {
                jdbcContext.destroy();
            }
        }
        return ret;
    }

    public SqlFileBatchUpdate param(Object param) {
        if (paramClass == null && param != null) {
            throw new IllegalParamTypeRuntimeException(null, param.getClass());
        } else if (paramClass != null && param != null) {
            if (paramClass != param.getClass()) {
                throw new IllegalParamTypeRuntimeException(paramClass, param
                        .getClass());
            }
        }
        paramsList.add(param);
        return this;
    }

    /**
     * SQLファイルのパスを返します。
     * 
     * @return SQLファイルのパス
     */
    public String getPath() {
        return path;
    }

    /**
     * 準備されたステートメントを返します。
     * 
     * @param jdbcContext
     *            JDBCコンテキスト
     * @return 準備されたステートメント
     */
    protected PreparedStatement getPreparedStatement(JdbcContext jdbcContext) {
        PreparedStatement ps = jdbcContext.getPreparedStatement(executedSql);
        if (queryTimeout > 0) {
            StatementUtil.setQueryTimeout(ps, queryTimeout);
        }
        return ps;
    }

    @Override
    protected void prepare(String methodName) {
        prepareCallerClassAndMethodName(methodName);
        prepareNode();
    }

    /**
     * SQLの解析ノードを準備します。
     * 
     * @throws ResourceNotFoundRuntimeException
     *             パスに対応するリソースが見つからない場合。
     * 
     */
    protected void prepareNode() throws ResourceNotFoundRuntimeException {
        node = NodeCache.getNode(path, jdbcManager.getDialect().getName());
        if (node == null) {
            logger.log("ESSR0709", new Object[] { callerClass.getName(),
                    callerMethodName });
            throw new ResourceNotFoundRuntimeException(path);
        }
    }

    /**
     * パラメータを準備します。
     * 
     * @param parameter
     *            パラメータ
     */
    protected void prepareParameter(Object parameter) {
        sqlContext = new SqlContextImpl();
        if (paramClass != null) {
            if (ValueTypes.isSimpleType(paramClass)) {
                sqlContext.addArg("$1", parameter, paramClass);
            } else {
                BeanDesc beanDesc = BeanDescFactory.getBeanDesc(paramClass);
                for (int i = 0; i < beanDesc.getPropertyDescSize(); i++) {
                    PropertyDesc pd = beanDesc.getPropertyDesc(i);
                    if (!pd.isReadable()) {
                        continue;
                    }
                    Object value = pd.getValue(parameter);
                    sqlContext.addArg(pd.getPropertyName(), value, pd
                            .getPropertyType());
                }
            }
        }
        node.accept(sqlContext);
        Object[] vars = sqlContext.getBindVariables();
        Class<?>[] types = sqlContext.getBindVariableTypes();
        int size = vars.length;
        for (int i = 0; i < size; i++) {
            addParam(vars[i], types[i]);
        }
    }

    /**
     * SQLを準備します。
     */
    protected void prepareSql() {
        executedSql = sqlContext.getSql();
    }
}