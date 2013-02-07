/**
 * Copyright 2013 Michael K. Werle
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.coruscations.logback.redis;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.MultiThreadedClaimStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SingleThreadedClaimStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ch.qos.logback.core.UnsynchronizedAppenderBase;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public abstract class RedisAppenderBase<E, F> extends UnsynchronizedAppenderBase<E> {

  // Buffer info
  private int bufferSize = 512;

  // Jedis info
  private String redisHostName = "127.0.0.1";
  private int redisPort = 6379;
  private int redisTimeout = 5000;
  private String redisPassword = null;
  private int redisDatabase = 0;

  protected JedisPool pool;
  private Disruptor<EventWrapper<E,F>> disruptor;
  private ExecutorService executor;

  // Must be volatile for shutdown
  private volatile RingBuffer<EventWrapper<E,F>> ringBuffer;

  @Override
  public void start() {
    this.pool = startJedisPool();
    EventFactory<EventWrapper<E, F>> eventFactory = new EventFactory<EventWrapper<E, F>>() {
      public EventWrapper<E, F> newInstance() {
        return new EventWrapper<E, F>();
      }
    };
    Disruptor<EventWrapper<E, F>> disruptor = createDisruptor(eventFactory);
    ringBuffer = disruptor.start();
    this.disruptor = disruptor;
    super.start();
  }

  @SuppressWarnings("unchecked")
  private Disruptor<EventWrapper<E, F>> createDisruptor(
      EventFactory<EventWrapper<E, F>> eventFactory) {
    executor = Executors.newCachedThreadPool();
    Disruptor<EventWrapper<E, F>> disruptor =
        new Disruptor<EventWrapper<E, F>>(eventFactory, executor,
                                          new MultiThreadedClaimStrategy(bufferSize),
                                          new SleepingWaitStrategy());
    disruptor.handleEventsWith(getEventFormatter()).then(getEventFlusher());
    disruptor.handleExceptionsWith(new ExceptionHandler() {
      @Override
      public void handleEventException(Throwable ex, long sequence, Object event) {
        addWarn("Failed to log even to Redis.", ex);
      }

      @Override
      public void handleOnStartException(Throwable ex) {
        addError("Failed to start Redis appender.", ex);
      }

      @Override
      public void handleOnShutdownException(Throwable ex) {
        addWarn("Failed to stop Redis appender.", ex);
      }
    });
    return disruptor;
  }

  private JedisPool startJedisPool() {
    JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
    // TODO: Add pool configuration properties.
    JedisPool pool = new JedisPool(jedisPoolConfig, redisHostName, redisPort, redisTimeout,
                                   redisPassword, redisDatabase);
    Jedis jedis = pool.getResource();
    try {
      jedis.select(0);
    } catch (Exception e) {
      addError("Cannot connect to " + redisHostName + ":" + redisPort, e);
      this.stop();
      return null;
    } finally {
      pool.returnResource(jedis);
    }
    return pool;
  }

  public abstract EventHandler<EventWrapper<E, F>> getEventFormatter();

  public abstract EventHandler<EventWrapper<E, F>> getEventFlusher();

  @SuppressWarnings("AssignmentToNull")
  @Override
  public void stop() {
    // Unconditionally set the ringBuffer to null because if events are published after
    //   the disruptor is shutdown, we will deadlock.
    ringBuffer = null;
    if (disruptor != null) {
      disruptor.shutdown();
      disruptor = null;
    }
    if (executor != null) {
      executor.shutdown();
      executor = null;
    }
    if (pool != null) {
      pool.destroy();
      pool = null;
    }
    super.stop();
  }


  @Override
  protected final void append(E eventObject) {
    preprocess(eventObject);
    long index = ringBuffer.next();
    ringBuffer.get(index).setValue(eventObject);
    ringBuffer.publish(index);
  }

  protected abstract void preprocess(E eventObject);

  public int getBufferSize() {
    return bufferSize;
  }

  public void setBufferSize(int bufferSize) {
    this.bufferSize = bufferSize;
  }

  public String getRedisHostName() {
    return redisHostName;
  }

  public void setRedisHostName(String redisHostName) {
    this.redisHostName = redisHostName;
  }

  public int getRedisPort() {
    return redisPort;
  }

  public void setRedisPort(int redisPort) {
    this.redisPort = redisPort;
  }

  public int getRedisTimeout() {
    return redisTimeout;
  }

  public void setRedisTimeout(int redisTimeout) {
    this.redisTimeout = redisTimeout;
  }

  public String getRedisPassword() {
    return redisPassword;
  }

  public void setRedisPassword(String redisPassword) {
    this.redisPassword = redisPassword;
  }

  public int getRedisDatabase() {
    return redisDatabase;
  }

  public void setRedisDatabase(int redisDatabase) {
    this.redisDatabase = redisDatabase;
  }

  public final class EventWrapper<E, F> {

    private E value;
    private F formatted;

    public E getValue() {
      return value;
    }

    public void setValue(final E value) {
      this.value = value;
    }

    public F getFormatted() {
      return formatted;
    }

    public void setFormatted(F formatted) {
      this.formatted = formatted;
    }
  }
}
