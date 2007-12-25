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
package org.seasar.extension.dbcp.impl;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.sql.XADataSource;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

import org.seasar.extension.dbcp.ConnectionPool;
import org.seasar.extension.dbcp.ConnectionWrapper;
import org.seasar.extension.timer.TimeoutManager;
import org.seasar.extension.timer.TimeoutTarget;
import org.seasar.extension.timer.TimeoutTask;
import org.seasar.framework.exception.SIllegalStateException;
import org.seasar.framework.log.Logger;
import org.seasar.framework.util.SLinkedList;
import org.seasar.framework.util.StringUtil;
import org.seasar.framework.util.TransactionManagerUtil;
import org.seasar.framework.util.TransactionUtil;

/**
 * {@link ConnectionPool}の実装クラスです。
 * 
 * @author higa
 * 
 */
public class ConnectionPoolImpl implements ConnectionPool {

    /**
     * readonly用のBindingアノテーションです。
     */
    public static final String readOnly_BINDING = "bindingType=may";

    /**
     * transactionIsolationLevel用のBindingアノテーションです。
     */
    public static final String transactionIsolationLevel_BINDING = "bindingType=may";

    /**
     * デフォルトのトランザクション分離レベルです。
     */
    public static final int DEFAULT_TRANSACTION_ISOLATION_LEVEL = -1;

    private static Logger logger = Logger.getLogger(ConnectionPoolImpl.class);

    private XADataSource xaDataSource;

    private TransactionManager transactionManager;

    private int timeout = 600;

    private int maxPoolSize = 10;

    private boolean allowLocalTx = true;

    private boolean readOnly = false;

    private int transactionIsolationLevel = DEFAULT_TRANSACTION_ISOLATION_LEVEL;

    private String validationQuery;

    private long validationInterval;

    private Set activePool = new HashSet();

    private Map txActivePool = new HashMap();

    private SLinkedList freePool = new SLinkedList();

    private TimeoutTask timeoutTask;

    /**
     * {@link ConnectionPoolImpl}を作成します。
     */
    public ConnectionPoolImpl() {
        timeoutTask = TimeoutManager.getInstance().addTimeoutTarget(
                new TimeoutTarget() {
                    public void expired() {
                    }
                }, Integer.MAX_VALUE, true);
    }

    /**
     * XAデータソースを返します。
     * 
     * @return XAデータソース
     */
    public XADataSource getXADataSource() {
        return xaDataSource;
    }

    /**
     * XAデータソースを設定します。
     * 
     * @param xaDataSource
     *            XAデータソース
     */
    public void setXADataSource(XADataSource xaDataSource) {
        this.xaDataSource = xaDataSource;
    }

    /**
     * トランザクションマネージャを返します。
     * 
     * @return トランザクションマネージャ
     */
    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    /**
     * トランザクションマネージャを設定します。
     * 
     * @param transactionManager
     *            トランザクションマネージャ
     */
    public void setTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * タイムアウトを返します。
     * 
     * @return タイムアウト
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * タイムアウトを設定します。
     * 
     * @param timeout
     *            タイムアウト
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    /**
     * プーリング可能な上限を設定します。
     * 
     * @param maxPoolSize
     *            プーリング可能な上限
     */
    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    /**
     * トランザクション外でコネクションの取得を許すかどうかを返します。
     * 
     * @return トランザクション外でコネクションの取得を許すかどうか
     */
    public boolean isAllowLocalTx() {
        return allowLocalTx;
    }

    /**
     * トランザクション外でコネクションの取得を許すかどうかを設定します。
     * 
     * @param allowLocalTx
     *            トランザクション外でコネクションの取得を許すかどうか
     */
    public void setAllowLocalTx(boolean allowLocalTx) {
        this.allowLocalTx = allowLocalTx;
    }

    /**
     * 読み取り専用かどうかを返します。
     * 
     * @return 読み取り専用かどうか
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * 読み取り専用かどうかを設定します。
     * 
     * @param readOnly
     *            読み取り専用かどうか
     */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    /**
     * トランザクション分離レベルを設定します。
     * 
     * @return トランザクション分離レベル
     */
    public int getTransactionIsolationLevel() {
        return transactionIsolationLevel;
    }

    /**
     * トランザクション分離レベルを設定します。
     * 
     * @param transactionIsolationLevel
     *            トランザクション分離レベル
     */
    public void setTransactionIsolationLevel(int transactionIsolationLevel) {
        this.transactionIsolationLevel = transactionIsolationLevel;
    }

    /**
     * コネクションの死活を確認する検証用クエリを返します。
     * 
     * @return 検証用クエリ
     */
    public String getValidationQuery() {
        return validationQuery;
    }

    /**
     * コネクションの死活を確認する検証用クエリを設定します。
     * <p>
     * <code>null</code>または空文字を指定した場合、検証は行われません。
     * </p>
     * 
     * @param validationQuery
     *            検証用クエリ
     */
    public void setValidationQuery(String validationQuery) {
        this.validationQuery = validationQuery;
    }

    /**
     * コネクションの死活を検証する間隔（ミリ秒）を返します。
     * 
     * @return 検証する間隔（ミリ秒）
     */
    public long getValidationInterval() {
        return validationInterval;
    }

    /**
     * コネクションの死活を検証する間隔（ミリ秒）を設定します。
     * <p>
     * <code>0</code>以下の値を指定した場合、検証は行われません。
     * </p>
     * 
     * @param validationInterval
     *            検証する間隔（ミリ秒）
     */
    public void setValidationInterval(long validationInterval) {
        this.validationInterval = validationInterval;
    }

    public int getActivePoolSize() {
        return activePool.size();
    }

    public int getTxActivePoolSize() {
        return txActivePool.size();
    }

    public int getFreePoolSize() {
        return freePool.size();
    }

    public synchronized ConnectionWrapper checkOut() throws SQLException {
        Transaction tx = getTransaction();
        if (tx == null && !isAllowLocalTx()) {
            throw new SIllegalStateException("ESSR0311", null);
        }

        ConnectionWrapper con = getConnectionTxActivePool(tx);
        if (con != null) {
            if (logger.isDebugEnabled()) {
                logger.log("DSSR0007", new Object[] { tx });
            }
            return con;
        }
        while (getMaxPoolSize() > 0
                && getActivePoolSize() + getTxActivePoolSize() >= getMaxPoolSize()) {
            try {
                wait();
            } catch (InterruptedException ignore) {
            }
        }
        con = checkOutFreePool(tx);
        if (con == null) {
            con = createConnection(tx);
        }
        if (tx == null) {
            setConnectionActivePool(con);
        } else {
            TransactionUtil.enlistResource(tx, con.getXAResource());
            TransactionUtil.registerSynchronization(tx,
                    new SynchronizationImpl(tx));
            setConnectionTxActivePool(tx, con);
        }
        con.setReadOnly(readOnly);
        if (transactionIsolationLevel != DEFAULT_TRANSACTION_ISOLATION_LEVEL) {
            con.setTransactionIsolation(transactionIsolationLevel);
        }
        if (logger.isDebugEnabled()) {
            logger.log("DSSR0007", new Object[] { tx });
        }
        return con;
    }

    private Transaction getTransaction() {
        return TransactionManagerUtil.getTransaction(transactionManager);
    }

    private ConnectionWrapper getConnectionTxActivePool(Transaction tx) {
        return (ConnectionWrapper) txActivePool.get(tx);
    }

    private ConnectionWrapper checkOutFreePool(final Transaction tx) {
        if (freePool.isEmpty()) {
            return null;
        }
        FreeItem item = (FreeItem) freePool.removeLast();
        ConnectionWrapper con = item.getConnection();
        con.init(tx);
        item.destroy();
        if (StringUtil.isEmpty(validationQuery)) {
            return con;
        }
        if (validateConnection(con, item.getPooledTime())) {
            return con;
        }
        return null;
    }

    private boolean validateConnection(final ConnectionWrapper con,
            final long pooledTime) {
        final long currentTime = System.currentTimeMillis();
        if (currentTime - pooledTime < validationInterval) {
            return true;
        }
        try {
            final PreparedStatement ps = con.prepareStatement(validationQuery);
            try {
                ps.executeQuery();
            } finally {
                ps.close();
            }
        } catch (final Exception e) {
            try {
                con.close();
            } catch (final Exception ignore) {
            }
            for (SLinkedList.Entry entry = freePool.getFirstEntry(); entry != null; entry = entry
                    .getNext()) {
                final FreeItem item = (FreeItem) entry.getElement();
                try {
                    item.getConnection().closeReally();
                } catch (final Exception ignore) {
                }
            }
            freePool.clear();
            logger.log("ESSR0096", null, e);
            return false;
        }
        return true;
    }

    private ConnectionWrapper createConnection(Transaction tx)
            throws SQLException {
        ConnectionWrapper con = new ConnectionWrapperImpl(xaDataSource
                .getXAConnection(), this, tx);
        if (logger.isDebugEnabled()) {
            logger.log("DSSR0006", null);
        }
        return con;
    }

    private void setConnectionTxActivePool(Transaction tx,
            ConnectionWrapper connection) {

        txActivePool.put(tx, connection);
    }

    private void setConnectionActivePool(ConnectionWrapper connection) {
        activePool.add(connection);
    }

    public synchronized void release(ConnectionWrapper connection) {
        activePool.remove(connection);
        Transaction tx = getTransaction();
        if (tx != null) {
            txActivePool.remove(tx);
        }
        connection.closeReally();
        notify();
    }

    public synchronized void checkIn(ConnectionWrapper connection) {
        activePool.remove(connection);
        connection.cleanup();
        checkInFreePool(connection);
    }

    private void checkInFreePool(ConnectionWrapper connection) {
        if (getMaxPoolSize() > 0) {
            freePool.addLast(new FreeItem(connection));
            notify();
        } else {
            connection.closeReally();
        }
    }

    public synchronized void checkInTx(Transaction tx) {
        if (tx == null) {
            return;
        }
        if (getTransaction() != null) {
            return;
        }
        ConnectionWrapper con = (ConnectionWrapper) txActivePool.remove(tx);
        if (con == null) {
            return;
        }
        checkInFreePool(con);
    }

    public final synchronized void close() {
        for (SLinkedList.Entry e = freePool.getFirstEntry(); e != null; e = e
                .getNext()) {
            FreeItem item = (FreeItem) e.getElement();
            item.getConnection().closeReally();
            item.destroy();
        }
        freePool.clear();
        for (Iterator i = txActivePool.values().iterator(); i.hasNext();) {
            ConnectionWrapper con = (ConnectionWrapper) i.next();
            con.closeReally();
        }
        txActivePool.clear();
        for (Iterator i = activePool.iterator(); i.hasNext();) {
            ConnectionWrapper con = (ConnectionWrapper) i.next();
            con.closeReally();
        }
        activePool.clear();
        timeoutTask.cancel();
    }

    private class FreeItem implements TimeoutTarget {

        private ConnectionWrapper connectionWrapper_;

        private TimeoutTask timeoutTask_;

        private long pooledTime;

        FreeItem(ConnectionWrapper connectionWrapper) {
            connectionWrapper_ = connectionWrapper;
            timeoutTask_ = TimeoutManager.getInstance().addTimeoutTarget(this,
                    timeout, false);
            pooledTime = System.currentTimeMillis();
        }

        /**
         * コネクションを返します。
         * 
         * @return コネクション
         */
        public ConnectionWrapper getConnection() {
            return connectionWrapper_;
        }

        /**
         * プールされた時刻（ミリ秒）を返します。
         * 
         * @return プールされた時刻（ミリ秒）
         */
        public long getPooledTime() {
            return pooledTime;
        }

        public void expired() {
            synchronized (ConnectionPoolImpl.this) {
                freePool.remove(this);
            }
            synchronized (this) {
                if (timeoutTask_ != null) {
                    timeoutTask_.cancel();
                    timeoutTask_ = null;
                }
                if (connectionWrapper_ != null) {
                    connectionWrapper_.closeReally();
                    connectionWrapper_ = null;
                }
            }
        }

        /**
         * 破棄します。
         */
        public synchronized void destroy() {
            if (timeoutTask_ != null) {
                timeoutTask_.cancel();
                timeoutTask_ = null;
            }
            connectionWrapper_ = null;
        }
    }

    /**
     * {@link Synchronization}の実装です。
     * 
     * @author koichik
     */
    public class SynchronizationImpl implements Synchronization {

        /** トランザクション */
        protected final Transaction tx;

        /**
         * インスタンスを構築します。
         * 
         * @param tx
         *            トランザクション
         */
        public SynchronizationImpl(final Transaction tx) {
            this.tx = tx;
        }

        public final void beforeCompletion() {
        }

        public void afterCompletion(final int status) {
            switch (status) {
            case Status.STATUS_COMMITTED:
            case Status.STATUS_ROLLEDBACK:
                checkInTx(tx);
                break;
            }
        }

    }

}
