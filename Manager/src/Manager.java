import ru.spbstu.pipeline.*;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Manager {
    Manager(String str) {
        filename = str;
    }

    RC work() {
        String[] lines = read();
        TreeMap<String, String> tokens = SyntacticManagerParser.parse(lines);
        ConfigParams cfgP = new ConfigParams();
        RC rc = SemanticManagerParser.parse(tokens, cfgP);
        if(rc != RC.CODE_SUCCESS) {
            logger.log(Level.SEVERE, "Error: ", rc);
            return rc;
        }

        try (FileInputStream fis = new FileInputStream(new File(cfgP.inputFile))) {
            IReader reader = null;
            try (FileOutputStream fos = new FileOutputStream(new File(cfgP.outputFile))) {
                IWriter writer = null;

                try {
                    reader = (IReader) Class.forName(cfgP.Executors[0].executor).getConstructor(Logger.class).newInstance(logger);
                    writer = (IWriter) Class.forName(cfgP.Executors[cfgP.Executors.length - 1].executor).getConstructor(Logger.class).newInstance(logger);
                    reader.setProducer(null);
                    writer.setConsumer(null);
                    reader.setInputStream(fis);
                    writer.setOutputStream(fos);

                    rc = reader.setConfig(cfgP.configForReader);
                    if (rc != RC.CODE_SUCCESS) {
                        logger.log(Level.SEVERE, "Error: ", rc);
                        return rc;
                    }
                    rc = writer.setConfig(cfgP.configForWriter);
                    if (rc != RC.CODE_SUCCESS) {
                        logger.log(Level.SEVERE, "Error: ", rc);
                        return rc;
                    }

                    IExecutor[] exe = new IExecutor[cfgP.Executors.length - 2];
                    for (int i = 1; i < cfgP.Executors.length - 1; ++i) {
                        exe[i - 1] = (IExecutor) Class.forName(cfgP.Executors[i].executor).getConstructor(Logger.class).newInstance(logger);
                    }

                    if (exe.length > 0) {
                        reader.setConsumer(exe[0]);
                        exe[0].setProducer(reader);
                        writer.setProducer(exe[exe.length - 1]);
                        exe[exe.length - 1].setConsumer(writer);

                        for (int i = 0; i < exe.length; ++i) {
                            if (i > 0) {
                                exe[i].setProducer(exe[i - 1]);
                            }
                            if (i < exe.length - 1) {
                                exe[i].setConsumer(exe[i + 1]);
                            }
                            rc = exe[i].setConfig(cfgP.Executors[i + 1].config);
                            if (rc != RC.CODE_SUCCESS) {
                                logger.log(Level.SEVERE, "Error: ", rc);
                                return rc;
                            }
                        }
                    }

                    reader.execute();

                } catch (ClassNotFoundException | InstantiationException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    logger.log(Level.SEVERE, "Exception: ", e);
                    return RC.CODE_FAILED_PIPELINE_CONSTRUCTION;
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Exception: ", e.getMessage());
                return RC.CODE_INVALID_OUTPUT_STREAM;
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Exception: ", e.getMessage());
            return RC.CODE_INVALID_INPUT_STREAM;
        }

        return RC.CODE_SUCCESS;
    }

    private String[] read() {
        ArrayList<String> lines = new ArrayList<>();
        try(BufferedReader reader = new BufferedReader(new FileReader(new File(filename)))) {
            String line;
            while((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Exception: ", e.getMessage());
        }
        return lines.toArray(new String[0]);
    }

    private final String filename;
    private static final Logger logger = Logger.getLogger(Manager.class.getName());
}

class SyntacticManagerParser {
    public static TreeMap<String, String> parse(String[] configData) {
        TreeMap<String, String> tokens = new TreeMap<>();
        ManagerGrammar grammar = new ManagerGrammar();
        for(String str : configData) {
            str = str.replaceAll("\\s+", "");
            String key = str.substring(0, str.indexOf(grammar.delimiter()));
            String value = str.substring(str.indexOf(grammar.delimiter()) + grammar.delimiter().length());
            tokens.put(key, value);
        }
        return tokens;
    }
}

class SemanticManagerParser {
    public static RC parse(TreeMap<String, String> tokens, ConfigParams cfgP) {
        ManagerGrammar grammar = new ManagerGrammar();
        TreeMap<String, String> executors = new TreeMap<>();
        ArrayList<String> orderExecutors = new ArrayList<>();
        for(Map.Entry<String, String> entry : tokens.entrySet()) {
            if(entry.getKey().equals(grammar.token(ManagerGrammarWords.INPUT.ordinal()))) {
                cfgP.inputFile = entry.getValue();
            } else if(entry.getKey().equals(grammar.token(ManagerGrammarWords.INPUT_CONFIG.ordinal()))) {
                cfgP.configForReader = entry.getValue();
            } else if(entry.getKey().equals(grammar.token(ManagerGrammarWords.OUTPUT.ordinal()))) {
                cfgP.outputFile = entry.getValue();
            } else if(entry.getKey().equals(grammar.token(ManagerGrammarWords.OUTPUT_CONFIG.ordinal()))) {
                cfgP.configForWriter = entry.getValue();
            } else if(entry.getKey().equals(grammar.token(ManagerGrammarWords.EXECUTOR.ordinal()))) {
                String str = entry.getValue();
                String executor = str.substring(0, str.indexOf(grammar.delimiter()));

                String config = str.substring(str.indexOf(grammar.delimiter()) + grammar.delimiter().length());
                executors.put(executor, config);
            } else if(entry.getKey().equals(grammar.token(ManagerGrammarWords.ORDER.ordinal()))) {
                String order = entry.getValue();
                order = order.replaceAll("\\s+", "");
                String[] os = order.split(grammar.delimiter());
                orderExecutors.addAll(Arrays.asList(os));
            } else {
                return RC.CODE_CONFIG_SEMANTIC_ERROR;
            }
        }
        cfgP.Executors = new StringPair[orderExecutors.size()];
        for(int i = 0; i < cfgP.Executors.length; ++i) {
            cfgP.Executors[i] = new StringPair();
            cfgP.Executors[i].executor = orderExecutors.get(i);
            cfgP.Executors[i].config = executors.get(cfgP.Executors[i].executor);
        }
        cfgP.Executors[0].config = cfgP.configForReader;
        cfgP.Executors[cfgP.Executors.length - 1].config = cfgP.configForWriter;
        return RC.CODE_SUCCESS;
    }
}

class StringPair {
    public String executor;
    public String config;
}

class ConfigParams {
    public String inputFile;
    public String outputFile;
    public String configForReader;
    public String configForWriter;
    public StringPair[] Executors;
}

class ManagerGrammar extends BaseGrammar {
    ManagerGrammar() {
        super(aTokens);
    }
    private static final String[] aTokens = new String[6];
    static {
        aTokens[0] = "InputFile";
        aTokens[1] = "ConfigForReader";
        aTokens[2] = "OutputFile";
        aTokens[3] = "ConfigForWriter";
        aTokens[4] = "ExecutorName";
        aTokens[5] = "Order";
    }
}

enum ManagerGrammarWords {
    INPUT,
    INPUT_CONFIG,
    OUTPUT,
    OUTPUT_CONFIG,
    EXECUTOR,
    ORDER
}

