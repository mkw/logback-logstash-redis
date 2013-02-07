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

import com.coruscations.logback.redis.RedisAppenderBase;
import com.lmax.disruptor.EventHandler;

import org.slf4j.Marker;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import redis.clients.jedis.Jedis;

public class RedisLogstashAppender extends RedisAppenderBase<ILoggingEvent, String> {

  private static final String[] ESCAPE_STRINGS = new String[]{
      "\\u0000", "\\u0001", "\\u0002", "\\u0003", "\\u0004", "\\u0005", "\\u0006", "\\u0007", "\\b",
      "\\t", "\\n", "\\u000B", "\\f", "\\r", "\\u000E", "\\u000F", "\\u0010", "\\u0011", "\\u0012",
      "\\u0013", "\\u0014", "\\u0015", "\\u0016", "\\u0017", "\\u0018", "\\u0019", "\\u001A",
      "\\u001B", "\\u001C", "\\u001D", "\\u001E", "\\u001F"};

  boolean includeCallerData = false;

  // Logstash information
  private String key = "logstash";
  private String type = "";
  private String hostName = null;
  private String file = "logback";
  private String source;

  public RedisLogstashAppender() {
    String hostName = null;
    try {
      hostName = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      // Ignore
    }
    if (hostName == null) {
      try {
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        // Prefer IPv4 addresses
        InetAddress inetAddress = null;
        while (networkInterfaces.hasMoreElements()) {
          NetworkInterface networkInterface = networkInterfaces.nextElement();
          List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();
          for (InterfaceAddress interfaceAddress : interfaceAddresses) {
            inetAddress = interfaceAddress.getAddress();
            if (inetAddress instanceof Inet4Address) {
              break;
            }
          }
        }
        if (inetAddress != null) {
          hostName = inetAddress.getHostName();
        }
      } catch (SocketException e) {
        // give up
      }
    }
    this.hostName = hostName;
    updateSource();
  }

  private final ThreadLocal<ISO8601Formatter> iso8601DateFormat =
      new ThreadLocal<ISO8601Formatter>() {
        @Override
        protected ISO8601Formatter initialValue() {
          return new ISO8601Formatter();
        }
      };


  @Override
  public EventHandler<EventWrapper<ILoggingEvent, String>> getEventFormatter() {
    return new LogstashEventFormatter();
  }

  @Override
  public EventHandler<EventWrapper<ILoggingEvent, String>> getEventFlusher() {
    return new LogstashEventFlusher();
  }

  @Override
  protected void preprocess(ILoggingEvent eventObject) {
    eventObject.prepareForDeferredProcessing();
    if (includeCallerData) {
      eventObject.getCallerData();
    }
  }

  public boolean isIncludeCallerData() {
    return includeCallerData;
  }

  public void setIncludeCallerData(boolean includeCallerData) {
    this.includeCallerData = includeCallerData;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    if (key == null || key.length() == 0) {
      throw new IllegalArgumentException("Key cannot be null or empty.");
    }
    this.key = escape(key, new StringBuilder(key.length())).toString();
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type == null ? "" : type;
    updateSource();
  }

  public String getHostName() {
    return hostName;
  }

  public void setHostName(String hostName) {
    this.hostName = hostName;
    updateSource();
  }

  public String getFile() {
    return file;
  }

  public void setFile(String file) {
    this.file = file;
    updateSource();
  }

  private void updateSource() {
    StringBuilder sb = new StringBuilder(127);
    escape((type.length() == 0 ? "" : (type + "://")) +
           hostName + "/" +
           (file == null ? "logback" : file), sb);
    this.source = sb.toString();
  }

  @SuppressWarnings("ImplicitNumericConversion")
  private static StringBuilder escape(String input, StringBuilder sb) {
    for (int i = 0, length = input.length(); i < length; i++) {
      char ch = input.charAt(i);
      switch (ch) {
        case '\\':
          sb.append("\\\\");
          break;
        case '"':
          sb.append('"');
          break;
        default:
          if (ch < 0x20) {
            sb.append(ESCAPE_STRINGS[ch]);
          } else {
            sb.append(ch);
          }
      }
    }
    return sb;
  }

  private class LogstashEventFlusher
      implements EventHandler<EventWrapper<ILoggingEvent, String>> {

    private final List<String> jsonStrings = new LinkedList<String>();

    @Override
    public void onEvent(EventWrapper<ILoggingEvent, String> event, long sequence,
                        boolean endOfBatch) {
      jsonStrings.add(event.getFormatted());
      if (endOfBatch) {
        String[] values = new String[jsonStrings.size()];
        Jedis jedis = pool.getResource();
        try {
          jedis.rpush(key, jsonStrings.toArray(values));
        } catch (Exception e) {
          addError("Failed to flush " + jsonStrings.size() + "log messages to " +
                   getRedisHostName() + ":" + getRedisPort());
        } finally {
          pool.returnResource(jedis);
        }
        // Clear regardless of success to we do not leak memory.
        jsonStrings.clear();
      }
    }
  }

  private class LogstashEventFormatter
      implements EventHandler<EventWrapper<ILoggingEvent, String>> {

    @Override
    public void onEvent(EventWrapper<ILoggingEvent, String> event, long sequence,
                        boolean endOfBatch) {
      event.setFormatted(formatEvent(event.getValue()));
    }

    private String formatEvent(ILoggingEvent event) {
      StringBuilder sb = new StringBuilder(2047);

      sb.append("{\"@source\":\"");
      escape(source, sb);
      sb.append("\",");

      sb.append("\"@tags\":[");
      appendTags(sb, event);
      sb.append("],");

      sb.append("\"@fields\":{");
      appendFields(sb, event);
      sb.append("},");

      String iso8601Date = iso8601DateFormat.get().format(event.getTimeStamp());
      sb.append("\"@timestamp\":\"").append(iso8601Date).append("\",");

      sb.append("\"@message\":\"").append(event.getFormattedMessage()).append("\",");
      sb.append("\"@type\":\"").append(type).append("\"}");
      return sb.toString();
    }

    void appendTags(StringBuilder sb, ILoggingEvent event) {
      boolean first = true;
      Marker marker = event.getMarker();
      if (marker != null) {
        sb.append("\"").append(marker.getName()).append("\"");
        first = false;
      }
      String tags = event.getMDCPropertyMap().get("tags");
      if (tags != null) {
        // TODO: Consider avoiding split() because of regex cost
        for (String tag : tags.split(",")) {
          if (!first) {
            sb.append(',');
          }
          sb.append("\"").append(tag).append("\"");
        }
      }
    }

    private void appendFields(StringBuilder sb, ILoggingEvent event) {
      // Start with things we might not always have
      IThrowableProxy throwableProxy = event.getThrowableProxy();
      if (throwableProxy != null) {
        appendField(sb, "stack_trace", ThrowableProxyUtil.asString(throwableProxy)).append(',');
      }
      Map<String, String> mdc = event.getMDCPropertyMap();
      for (Map.Entry<String, String> entry : mdc.entrySet()) {
        appendField(sb, entry.getKey(), entry.getValue()).append(',');
      }

      // We always have these
      appendField(sb, "logger_name", event.getLoggerName()).append(',');
      appendField(sb, "thread_name", event.getThreadName()).append(',');
      // Is there any value to this?
      //appendField(sb, "level_value", String.valueOf(event.getLevel().toInt())).append(',');
      appendField(sb, "level", event.getLevel().toString());
    }

    private StringBuilder appendField(StringBuilder sb, String name, String value) {
      sb.append('"');
      escape(name, sb);
      //    sb.append("\":[\"");
      sb.append("\":\"");
      escape(value, sb);
      //    sb.append("\"]");
      sb.append('"');
      return sb;
    }
  }

  private class ISO8601Formatter {

    DateFormat dateFormat;
    Date date;

    private ISO8601Formatter() {
      this.dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      date = new Date();
    }

    private String format(long timestamp) {
      date.setTime(timestamp);
      return dateFormat.format(date);
    }
  }
}
