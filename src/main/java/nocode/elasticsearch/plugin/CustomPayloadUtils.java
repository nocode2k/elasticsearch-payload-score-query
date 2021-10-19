package nocode.elasticsearch.plugin;

import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.queries.payloads.*;
import org.apache.lucene.util.BytesRef;

public class CustomPayloadUtils {

    public static PayloadDecoder getPayloadDecoder(String encoder) {
        PayloadDecoder decoder = null;
        if ("integer".equals(encoder)) {
            decoder = (BytesRef payload) -> payload == null ? 1 : PayloadHelper.decodeInt(payload.bytes, payload.offset);
        }
        if ("float".equals(encoder)) {
            decoder = (BytesRef payload) -> payload == null ? 1 : PayloadHelper.decodeFloat(payload.bytes, payload.offset);
        }
        return decoder;
    }

    public static PayloadFunction getPayloadFunction(String func) {
        PayloadFunction payloadFunction = null;
        if ("min".equals(func)) {
            payloadFunction = new MinPayloadFunction();
        }
        if ("max".equals(func)) {
            payloadFunction = new MaxPayloadFunction();
        }
        if ("average".equals(func)) {
            payloadFunction = new AveragePayloadFunction();
        }
        if ("sum".equals(func)) {
            payloadFunction = new SumPayloadFunction();
        }
        return payloadFunction;
    }

}
