/*
 * Copyright (c) 2010-2010 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.larex.io.connector;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.larex.io.ByteBuffers;
import org.codehaus.larex.io.Connection;
import org.codehaus.larex.io.ConnectionFactory;
import org.codehaus.larex.io.ReadWriteSelector;
import org.codehaus.larex.io.RuntimeIOException;
import org.codehaus.larex.io.Selector;
import org.codehaus.larex.io.ThreadLocalByteBuffers;

/**
 * @version $Revision$ $Date$
 */
public class StandardClientConnector
{
    private final Executor threadPool;
    private final ScheduledExecutorService scheduler;
    private final ByteBuffers byteBuffers;
    private final Selector[] selectors;
    private final AtomicInteger selector = new AtomicInteger();

    public StandardClientConnector(Executor threadPool, ScheduledExecutorService scheduler)
    {
        this(threadPool, scheduler, 1);
    }

    public StandardClientConnector(Executor threadPool, ScheduledExecutorService scheduler, int selectors)
    {
        this.threadPool = threadPool;
        this.scheduler = scheduler;
        this.byteBuffers = newByteBuffers();
        if (selectors < 1)
            throw new IllegalArgumentException("Invalid selectors count " + selectors + ": must be >= 1");
        this.selectors = new Selector[selectors];
        for (int i = 0; i < selectors; ++i)
            this.selectors[i] = newAsyncSelector();
    }

    protected ByteBuffers newByteBuffers()
    {
        return new ThreadLocalByteBuffers();
    }

    protected Selector newAsyncSelector()
    {
        return new ReadWriteSelector();
    }

    public <T extends Connection> Endpoint<T> newEndpoint(ConnectionFactory<T> connectionFactory)
    {
        Selector selector = chooseSelector(selectors);
        try
        {
            SocketChannel channel = SocketChannel.open();
            return newEndpoint(channel, selector, connectionFactory, byteBuffers, threadPool, scheduler);
        }
        catch (IOException x)
        {
            throw new RuntimeIOException(x);
        }
    }

    protected Selector chooseSelector(Selector[] selectors)
    {
        int index = selector.incrementAndGet();
        index = Math.abs(index % selectors.length);
        return selectors[index];
    }

    protected <T extends Connection> Endpoint<T> newEndpoint(SocketChannel channel, Selector selector, ConnectionFactory<T> connectionFactory, ByteBuffers byteBuffers, Executor threadPool, ScheduledExecutorService scheduler)
    {
        return new StandardEndpoint<T>(channel, selector, connectionFactory, byteBuffers, threadPool, scheduler);
    }
}