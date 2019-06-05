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
package org.apache.rocketmq.store;

import java.util.concurrent.atomic.AtomicLong;

public abstract class ReferenceResource {

    // 引用计数,>0可用，<=0不可用
    protected final AtomicLong refCount = new AtomicLong(1);

    // 是否可用
    protected volatile boolean available = true;

    // 是否清理干净
    protected volatile boolean cleanupOver = false;

    // 第一次shutdown时间
    private volatile long firstShutdownTimestamp = 0;

    /**
     * 和release函数搭配使用
     * 占用资源,refCount + 1
     *
     * @return
     */
    public synchronized boolean hold() {
        if (this.isAvailable()) {
            if (this.refCount.getAndIncrement() > 0) {
                return true;
            } else {
                this.refCount.getAndDecrement();
            }
        }

        return false;
    }

    public boolean isAvailable() {
        return this.available;
    }

    /**
     * 参数 intervalForcibly 代表强制间隔，即两次生效的间隔至少要有这么大(不是至多!!!)
     * 第一次调用时available设置为false，设置初始时间，释放一个引用
     * 之后再调用的时候，如果refCount > 0,且超过了强制间隔，则设置为一个负数，释放一个引用
     *
     * 备注：如果在intervalForcibly时间内再次shutdown 代码不会执行任何逻辑
     */
    public void shutdown(final long intervalForcibly) {
        if (this.available) {
            this.available = false;
            this.firstShutdownTimestamp = System.currentTimeMillis();
            this.release();
        } else if (this.getRefCount() > 0) {
            if ((System.currentTimeMillis() - this.firstShutdownTimestamp) >= intervalForcibly) {
                this.refCount.set(-1000 - this.getRefCount());
                this.release();
            }
        }
    }

    /**
     * 和hold函数搭配
     * 释放一个引用，计数-1
     * 若计数 <=0，则调用cleanup，子类实现
     */
    public void release() {
        long value = this.refCount.decrementAndGet();
        if (value > 0)
            return;

        synchronized (this) {

            this.cleanupOver = this.cleanup(value);
        }
    }

    public long getRefCount() {
        return this.refCount.get();
    }

    public abstract boolean cleanup(final long currentRef);

    public boolean isCleanupOver() {
        return this.refCount.get() <= 0 && this.cleanupOver;
    }
}
