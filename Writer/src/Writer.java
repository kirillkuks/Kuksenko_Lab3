import ru.spbstu.pipeline.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Writer implements IWriter {
    public Writer(Logger log) {
        logger = log;
    }

    @Override
    public RC execute() {
        byte[] bytes = handleData();

        if(bytes == null) {
            return RC.CODE_SUCCESS;
        }

        try {
            writer.write(bytes);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Expression: ", e.getMessage());
            return RC.CODE_FAILED_TO_WRITE;
        }
        return RC.CODE_SUCCESS;
    }

    private byte[] handleData() {
        Object data = mediator.getData();
        if(data == null) {
            return null;
        }
        switch (type) {
            case BYTE: {
                return (byte[]) data;
            }
            case CHAR: {
                byte[] byteData = new byte[((char[]) data).length];
                for (int i = 0; i < ((char[]) data).length; ++i) {
                    byteData[i] = (byte) ((char[]) data)[i];
                }
                return byteData;
            }
            case SHORT: {
                byte[] byteData = new byte[((short[]) data).length * 2];
                ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(((short[]) data));
                return byteData;
            }
        }
        return null;
    }

    @Override
    public RC setOutputStream(FileOutputStream fos) {
        writer = fos;
        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setConfig(String configName) {
        config = configName;
        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setProducer(IProducer prod) {
        producer = prod;
        type = null;
        for(TYPE t : types) {
            for(TYPE t1 : producer.getOutputTypes()) {
                if(t == t1) {
                    type = t;
                    break;
                }
            }
            if(type != null) {
                break;
            }
        }
        if(type == null) {
            return RC.CODE_FAILED_PIPELINE_CONSTRUCTION;
        }
        mediator = producer.getMediator(type);
        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setConsumer(IConsumer cons) {
        consumer = cons;
        return RC.CODE_SUCCESS;
    }

    private IConsumer consumer;
    private IProducer producer;
    private String config;
    private FileOutputStream writer;
    private static final TYPE[] types = {TYPE.BYTE, TYPE.SHORT};
    private final Logger logger;
    private IMediator mediator;
    private TYPE type;
}
