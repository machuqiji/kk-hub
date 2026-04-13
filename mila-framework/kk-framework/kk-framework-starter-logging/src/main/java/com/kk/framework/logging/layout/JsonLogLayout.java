package com.kk.framework.logging.layout;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public class JsonLogLayout extends LayoutWrappingEncoder<ILoggingEvent> {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final String TRACE_ID_KEY = "traceId";

    @Override
    public void doEncode(ILoggingEvent event, OutputStream out) throws IOException {
        Map<String, String> mdc = event.getMDCPropertyMap();
        String traceId = mdc.getOrDefault(TRACE_ID_KEY, null);

        JsonGenerator jg = mapper.getFactory().createGenerator(out);
        jg.writeStartObject();
        jg.writeStringField("timestamp", event.getInstant().toString());
        jg.writeStringField("level", event.getLevel().toString());
        jg.writeStringField("logger", event.getLoggerName());
        jg.writeStringField("message", event.getFormattedMessage());

        if (traceId != null) {
            jg.writeStringField(TRACE_ID_KEY, traceId);
        }

        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            jg.writeStringField("error", throwable.getMessage());
        } else {
            jg.writeNullField("error");
        }

        jg.writeEndObject();
        jg.writeRaw('\n');
        jg.flush();
    }

    @Override
    public byte[] headerBytes() {
        return new byte[0];
    }
}