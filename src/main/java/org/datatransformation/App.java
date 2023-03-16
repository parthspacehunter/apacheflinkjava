package org.datatransformation;

import org.Constants;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.util.Collector;
import org.model.LogRecord;
import software.amazon.kinesis.connectors.flink.FlinkKinesisConsumer;
import software.amazon.kinesis.connectors.flink.FlinkKinesisProducer;
import software.amazon.kinesis.connectors.flink.config.ConsumerConfigConstants;

import java.util.Properties;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStream<String> inputStream = createSource(env);

        DataStream<LogRecord> logRecords = mapStringToLogRecord(inputStream);

        DataStream<LogRecord> errorRecords = filterErrorRecords(logRecords);

        DataStream<LogRecord> keyedStream = assignIPasKey(errorRecords);


        DataStream<String> keyedStreamAsText = mapLogRecordToString(keyedStream);

        //TODO Uncomment this code for deploying to Kinesis Data Analytics

//        keyedStream.addSink(createSink());

        keyedStreamAsText.print();

        env.execute("Error alerts");

    }



    private static DataStream<String> mapLogRecordToString(DataStream<LogRecord> keyedStream) {
        DataStream<String> keyedStreamAsText = keyedStream.flatMap(new FlatMapFunction<LogRecord, String>() {

            @Override
            public void flatMap(LogRecord value, Collector<String> out) throws Exception {
                out.collect(value.getUrl()+"::" + value.getHttpStatus());
            }
        });
        return keyedStreamAsText;
    }



    private static DataStream<LogRecord> assignIPasKey(DataStream<LogRecord> errorRecords) {
        DataStream<LogRecord> keyedStream = errorRecords.keyBy(value -> value.getIp());
        return keyedStream;
    }



    private static DataStream<LogRecord> filterErrorRecords(DataStream<LogRecord> logRecords) {
        DataStream<LogRecord> errorRecords = logRecords.filter(new FilterFunction<LogRecord>() {

            @Override
            public boolean filter(LogRecord value) throws Exception {
                boolean matched = !value.getHttpStatus().equalsIgnoreCase("200");

                return matched;
            }
        });
        return errorRecords;
    }



    private static DataStream<LogRecord> mapStringToLogRecord(DataStream<String> inputStream) {
        System.out.println("------------------input----------");
        System.out.println(inputStream);
        DataStream<LogRecord> logRecords = inputStream.flatMap(new FlatMapFunction<String, LogRecord>() {

            @Override
            public void flatMap(String value, Collector<LogRecord> out) throws Exception {

                String[] parts = value.split("\\s+");

                LogRecord record = new LogRecord();
                record.setIp(parts[0]);
                record.setHttpStatus(parts[8]);
                record.setUrl(parts[6]);

                out.collect(record);

            }

        });
        return logRecords;
    }



	/*private static void createSink(final StreamExecutionEnvironment env, DataStream<LogRecord> input) {
		input.print();
	}*/

//    private static DataStream<String> createSource(final StreamExecutionEnvironment env) {
//        return env.readTextFile(
//                FILE_PATH);
//    }

    //TODO Uncomment this code for deploying to Kinesis Data Analytics
	private static DataStream<String> createSource(final StreamExecutionEnvironment env) {
		Properties inputProperties = new Properties();
		inputProperties.setProperty(ConsumerConfigConstants.AWS_REGION, Constants.AWS_REGION.toString());
		inputProperties.setProperty(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "LATEST");
		String inputStreamName = "ApplicationEvents";
		return env.addSource(new FlinkKinesisConsumer<>(inputStreamName, new SimpleStringSchema(), inputProperties));
	}

    private static SinkFunction<LogRecord> createSink() {
        Properties outputProperties = new Properties();
        outputProperties.setProperty(ConsumerConfigConstants.AWS_REGION, Constants.AWS_REGION.toString());
        SinkFunction<LogRecord> sink1 = new SinkFunction<LogRecord>() {
            @Override
            public void invoke(LogRecord value, Context context) throws Exception {
                SinkFunction.super.invoke(value, context);
            }
        };
//        FlinkKinesisProducer<String> sink = new FlinkKinesisProducer<>(new SimpleStringSchema(), outputProperties);
//        String outputStreamName = "ApplicationEvents";
//        sink.setDefaultStream(outputStreamName);
//        sink.setDefaultPartition("0");

        return sink1;
    }
}
