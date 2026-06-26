package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileWriter;

public class JsonLogger {

    private final ObjectMapper mapper =
            new ObjectMapper();

        public String write(SystemLog log)
            throws Exception {

        String json =
                mapper.writeValueAsString(log);

        try(FileWriter writer =
                    new FileWriter(
                            "system_logs.jsonl",
                            true
                    )) {

            writer.write(json);
            writer.write("\n");
        }

                return json;
        }

        public String toJson(SystemLog log)
                        throws Exception {

                return mapper.writeValueAsString(log);
    }
}