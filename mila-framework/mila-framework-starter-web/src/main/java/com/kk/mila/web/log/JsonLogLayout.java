package com.kk.mila.web.log;

import net.logstash.logback.encoder.LogstashEncoder;

import java.util.ArrayList;
import java.util.List;

public class JsonLogLayout extends LogstashEncoder {

    public JsonLogLayout() {
        setCustomFields("{\"app\":\"mila-framework\"}");
        setIncludeMdcKeyNames(getMdcFields());
        setIncludeContext(true);
        setIncludeTags(true);
        setIncludeCallerData(true);
        setTimestampPattern("yyyy-MM-dd HH:mm:ss.SSS");
    }

    private static List<String> getMdcFields() {
        List<String> fields = new ArrayList<>();
        fields.add("traceId");
        fields.add("userId");
        fields.add("requestUri");
        fields.add("method");
        fields.add("costTime");
        return fields;
    }
}
