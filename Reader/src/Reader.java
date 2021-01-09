import ru.spbstu.pipeline.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Reader implements IReader {
    public Reader(Logger log) {
        logger = log;
    }

    @Override
    public RC execute() {
        int readied;
        RC rc;
        bytes = new byte[cfgP.size];
        do {
            readied = read(bytes);
            if(readied < 0) {
                bytes = null;
            }
            if(readied != cfgP.size && bytes != null) {
                bytes = Arrays.copyOf(bytes, readied);
            }

            //System.out.println("<" + new String(bytes) + ">");
            rc = consumer.execute();
            if(rc != RC.CODE_SUCCESS) {
                return rc;
            }

            if(bytes == null) {
                return RC.CODE_SUCCESS;
            }

        } while(readied == cfgP.size);
        return RC.CODE_SUCCESS;
    }

    private int read(byte[] bytes) {
        int readied = 0;
        try {
            readied = reader.read(bytes, 0, cfgP.size);
            if(readied != cfgP.size && readied >= 0) {
                logger.log(Level.INFO, "File is over!");
                System.out.println("File is over!");
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Exception: ", e.getMessage());
        }
        return readied;
    }

    @Override
    public TYPE[] getOutputTypes() {
        return types;
    }

    @Override
    public IMediator getMediator(TYPE type) {
        switch (type) {
            case BYTE:
                return new ByteMediator();
            case CHAR:
                return new CharMediator();
            case SHORT:
                return new ShortMediator();
        }
        return null;
    }

    @Override
    public RC setInputStream(FileInputStream fis) {
        reader = fis;
        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setConfig(String configName) {
        config = configName;
        String[] lines = readConfig();
        if(lines == null) {
            return RC.CODE_CONFIG_GRAMMAR_ERROR;
        }
        TreeMap<String, String> tokens = SyntacticReaderParser.parse(lines);
        cfgP = new ConfigReaderParams();
        return SemanticReaderParser.parse(tokens, cfgP);
    }

    private String[] readConfig() {
        ArrayList<String> lines = new ArrayList<>();
        try(BufferedReader reader = new BufferedReader(new FileReader(new File(config)))) {
            String line;
            while((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Exception: ", e);
            return null;
        }
        return lines.toArray(new String[0]);
    }

    @Override
    public RC setProducer(IProducer prod) {
        producer = prod;
        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setConsumer(IConsumer cons) {
        consumer = cons;
        return RC.CODE_SUCCESS;
    }

    public class ByteMediator implements IMediator {
        @Override
        public Object getData() {
            if(bytes == null) {
                return null;
            }
            return Arrays.copyOf(bytes, bytes.length);
        }
    }

    public class CharMediator implements IMediator {
        @Override
        public Object getData() {
            if(bytes == null) {
                return null;
            }
            char[] chars = new char[bytes.length];
            for(int i = 0; i < bytes.length; ++i) {
                chars[i] = (char) bytes[i];
            }
            return chars;
        }
    }

    public class ShortMediator implements IMediator {
        public Object getData() {
            if(bytes == null) {
                return null;
            }
            short[] shorts = new short[bytes.length / 2];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
            return shorts;
        }
    }

    private IConsumer consumer;
    private IProducer producer;
    private String config;
    private FileInputStream reader;
    private static final TYPE[] types = {TYPE.BYTE, TYPE.CHAR};
    private final Logger logger;

    private ConfigReaderParams cfgP;
    private byte[] bytes;
}

class SyntacticReaderParser {
    public static TreeMap<String, String> parse(String[] configData) {
        TreeMap<String, String> tokens = new TreeMap<>();
        ReaderGrammar grammar = new ReaderGrammar();
        for(String str : configData) {
            String key = str.substring(0, str.indexOf(grammar.delimiter()));
            key = key.substring(0, key.lastIndexOf(' '));
            String value = str.substring(str.indexOf(grammar.delimiter()));
            value = value.substring(value.indexOf(' ') + 1);
            tokens.put(key, value);
        }
        return tokens;
    }
}

class SemanticReaderParser {
    public static RC parse(TreeMap<String, String> tokens, ConfigReaderParams cfgP) {
        ReaderGrammar grammar = new ReaderGrammar();
        for(Map.Entry<String, String> entry : tokens.entrySet()) {
            if(entry.getKey().equals(grammar.token(ReaderGrammarWords.SIZE.ordinal()))) {
                cfgP.size = Integer.parseInt(entry.getValue());
            } else {
                return RC.CODE_CONFIG_SEMANTIC_ERROR;
            }
        }
        return RC.CODE_SUCCESS;
    }
}

class ConfigReaderParams {
    public int size;
}

class ReaderGrammar extends BaseGrammar {
    ReaderGrammar() {
        super(aTokens);
    }
    private static final String[] aTokens = new String[4];
    static {
        aTokens[0] = "Size";
        aTokens[1] = "Mode";
        aTokens[2] = "Encode";
        aTokens[3] = "Decode";
    }
}

enum ReaderGrammarWords {
    SIZE
}
