package com.wuming.rag.util;

import com.wuming.api.rag.dto.RagDocument;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class RedisByteUtil {

    public static Map<byte[], byte[]> hashFields(RagDocument doc, float[] embedding){
        Map<byte[], byte[]> fields = new HashMap<>();

        fields.put(bytes("text"), bytes(doc.getText()));
        fields.put(bytes("embedding"), vectorBytes(embedding));

        if(doc.getMetadata() != null){
            for(Map.Entry<String, Object> entry : doc.getMetadata().entrySet()){
                if(entry.getValue() != null){
                    fields.put(bytes(entry.getKey()), bytes(entry.getValue().toString()));
                }
            }
        }
        return fields;
    }

    private static byte[] vectorBytes(float[] vector){
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for(float f : vector){
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    private static byte[] bytes(String value){
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
