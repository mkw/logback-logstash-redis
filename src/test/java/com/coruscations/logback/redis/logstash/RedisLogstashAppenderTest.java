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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;

import static org.junit.Assert.assertNotNull;

public class RedisLogstashAppenderTest {

  @Test
  public void testRedisLogstashAppenderProgrammatically() {
    RedisLogstashAppender appender = new RedisLogstashAppender();
    appender.setType("test-logback-redis-logstash");
    appender.setFile("test");
    appender.setRedisHostName("lmwerle.ticom-geo.com");
    appender.setRedisPort(6379);
    appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
    appender.start();
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.setLevel(Level.INFO);
    root.addAppender(appender);
    Logger logger = (Logger) LoggerFactory.getLogger(RedisLogstashAppenderTest.class);
    logger.info("This is a programmatic test.");
    logger.info("This is a programmatic test with an exception.", new Exception());
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
