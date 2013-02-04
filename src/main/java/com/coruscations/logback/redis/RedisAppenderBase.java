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

import ch.qos.logback.core.UnsynchronizedAppenderBase;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public abstract class RedisAppenderBase<E> extends UnsynchronizedAppenderBase<E> {

  private String redisHostName = "127.0.0.1";
  private int redisPort = 6379;
  private int redisTimeout = 5000;
  private String redisPassword = null;
  private int redisDatabase = 0;

  protected JedisPool pool;

  @Override
  public void start() {
    JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
    // TODO: Add pool configuration properties.
    JedisPool pool = new JedisPool(jedisPoolConfig, redisHostName, redisPort, redisTimeout,
                                   redisPassword, redisDatabase);
    Jedis jedis = pool.getResource();
    try {
      jedis.select(0);
    } finally {
      pool.returnResource(jedis);
    }
    this.pool = pool;
    super.start();
  }

  @Override
  public void stop() {
    if (pool != null) {
      pool.destroy();
    }
    super.stop();
  }

  @Override
  public boolean isStarted() {
    // Todo: Create an implementation for this method.
    return super.isStarted();
  }

  @Override
  protected abstract void append(E eventObject);

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
}
