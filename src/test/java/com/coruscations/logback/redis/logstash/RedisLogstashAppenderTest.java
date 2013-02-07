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
import org.slf4j.MDC;
import org.slf4j.MarkerFactory;

import java.net.URL;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.filter.ThresholdFilter;
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

    // Setup the test logger
    final Logger testLogger = (Logger) LoggerFactory.getLogger(getClass().getName() + "-test");
    testLogger.setLevel(Level.TRACE);
    testLogger.addAppender(appender);

    // Add a threshold filter to any existing loggers to lower verbosity
    Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    ThresholdFilter thresholdFilter = new ThresholdFilter();
    thresholdFilter.setLevel("INFO");
    thresholdFilter.start();
    Iterator<Appender<ILoggingEvent>> appenderIterator = rootLogger.iteratorForAppenders();
    while (appenderIterator.hasNext()) {
      appenderIterator.next().addFilter(thresholdFilter);
    }

    testLogger.info("This is a simple test.");
    testLogger.info("This is a multi-\nline\ntest.");
    testLogger.info("This is a \"quoted\" test.");

    int threadCount = 2 * Runtime.getRuntime().availableProcessors();
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    for (int i = 0; i < threadCount; i++) {
      final int threadNum = i;
      executor.submit(new Runnable() {
        private Random random = new Random();

        @Override
        public void run() {
          testLogger.info("Starting test thread {}.", threadNum);
          for (int i = 0; i < 1000; i++) {
            testLogger.trace("This is programmatic test {}:{}.", threadNum, i);
            if (random.nextInt(10) < 1) {
              try {
                Thread.sleep((long) random.nextInt(100));
              } catch (InterruptedException e) {
                // Ignore; it's just test code
              }
            }
          }
          testLogger.info("Finished test thread {}.", threadNum);
        }
      });
    }
    executor.shutdown();

    // Wait an absurdly long period of time
    executor.awaitTermination(30l, TimeUnit.SECONDS);
    testLogger.info("This is a programmatic test with an exception.", new Exception());
    testLogger.info("This is a programmatic test with a multi-line exception message.",
                    new Exception("Multi-\nline\nmessage"));
    testLogger.info("This is a programmatic test with a quoted exception message.",
                    new Exception("\"Quoted\" message"));
    MDC.put("A key", "A value");
    MDC.put("A multi-\nline\nkey", "A multi-\nline\nvalue");
    MDC.put("A \"quoted\" key", "A \"quoted\" value");
    testLogger.info("This is a programmatic test with an MDC.");
    MDC.clear();
    testLogger.info(MarkerFactory.getMarker("A marker"),
                    "This is a programmatic test for a marker.");
    testLogger.info(MarkerFactory.getMarker("A multi-\nline\nmarker"),
                    "This is a programmatic test for a multi-line marker.");
    testLogger.info(MarkerFactory.getMarker("A \"quoted\" marker"),
                    "This is a programmatic test for a quoted marker.");
    testLogger.info(MarkerFactory.getDetachedMarker("A detached marker"),
                    "This is a programmatic test for a detached marker.");
    testLogger.info(MarkerFactory.getDetachedMarker("A detached multi-\nline\nmarker"),
                    "This is a programmatic test for a detached multi-line marker.");
    testLogger.info(MarkerFactory.getDetachedMarker("A detached \"quoted\" marker"),
                    "This is a programmatic test for a detached quoted marker.");
    MDC.clear();
    MDC.put("tags", "one, two, thee, multi-\nline, \"quoted\" tag");
    testLogger.info("This is a programmatic test for tags.");
    MDC.clear();
    Thread.currentThread().setName("Multi-\nline\nThread\nName");
    testLogger.info("This is a programmatic test for multi-line thread names.");
    Thread.currentThread().setName("\"Quoted\" Thread Name");
    testLogger.info("This is a programmatic test for quoted thread names.");
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
