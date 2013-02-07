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
package com.coruscations.logback.redis.logstash;

import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.joran.spi.JoranException;

import static org.junit.Assert.assertNotNull;

public class RedisLogstashAppenderTest {

  @Test
  public void testRedisLogstashAppenderProgrammatically() throws InterruptedException {
    RedisLogstashAppender appender = new RedisLogstashAppender();
    appender.setType("test-logback-redis-logstash");
    appender.setFile("test");
    appender.setRedisHostName("localhost");
    appender.setRedisPort(6379);
    appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
    appender.start();
    final Logger testLogger = (Logger) LoggerFactory.getLogger(getClass().getName() + "-test");
    // Remove the existing appenders.
    testLogger.setAdditive(false);
    testLogger.detachAndStopAllAppenders();
    testLogger.setLevel(Level.INFO);
    testLogger.addAppender(appender);
    int threadCount = 2 * Runtime.getRuntime().availableProcessors();
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    for (int i = 0; i < threadCount; i++) {
      final int threadNum = i;
      executor.submit(new Runnable() {
        private Random random = new Random();
        @Override
        public void run() {
          for (int i = 0; i < 1000; i++) {
            testLogger.info("This is programmatic test {}:{}.", threadNum, i);
            if (random.nextInt(10) < 1) {
              try {
                Thread.sleep((long) random.nextInt(100));
              } catch (InterruptedException e) {
                // Ignore; it's just test code
              }
            }
          }
        }
      });
    }
    executor.shutdown();
    // Wait an absurdly long period of time
    executor.awaitTermination(30, TimeUnit.SECONDS);
    testLogger.info("This is a programmatic test with an exception.", new Exception());
    appender.stop();
  }

  @Test
  public void testRedisLogstashAppenderFile() throws JoranException {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    JoranConfigurator jc = new JoranConfigurator();
    jc.setContext(context);
    context.reset(); // override default configuration
    URL resource = getClass().getClassLoader().getResource("test-logback.xml");
    assertNotNull("Could not find test-logback.xml", resource);
    jc.doConfigure(resource.getFile());
    Logger logger = (Logger) LoggerFactory.getLogger(RedisLogstashAppenderTest.class);
    logger.info("This is a test.");
    logger.info("This is a test with an exception.", new Exception());
  }
}
