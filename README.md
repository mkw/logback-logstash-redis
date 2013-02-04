# Primitive Logstash Redis Appender

_Warning_: This is an extremely primitive attempt at an appender.  The parts I have used have been
tested, but several methods have proably never been tested.  If I get time, I may finsih it, but if
that is not fast enough for you, feel free to create a pull requst.

Available appender properties (default):
* redisHostName ("127.0.0.1")
* redisPort (6379)
* redisTimeout (5000) // milliseconds
* redisPassword (null)
* redisDatabase (0)
* key ("logstash")
* type ("")
* hostName (null)
* file ("logback")

Exmple for logback.xml:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="logstash" class="com.coruscations.logback.redis.logstash.RedisLogstashAppender">
        <redisHostName>localhost</redisHostName>
        <type>a-log-type</type>
    </appender>
    <root level="all">
        <appender-ref ref="logstash"/>
    </root>
</configuration>
```

Example for logstash.conf:

```
input {
  redis {
    host => "redis-host"
    type => "a-log-type"
    # This key must match the key specified in the appender configuration; the default is "logstash"
    key => "logstash"

    # The messages are in a list in the json_event format.
    data_type => "list"
    format => "json_event"
  }
}
```