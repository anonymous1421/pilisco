package pilisco;

import common.metrics.Metrics;
import common.util.Util;
import component.operator.Operator;
import component.operator.in1.filter.FilterFunction;
import component.operator.in1.map.FlatMapFunction;
import component.sink.Sink;
import component.sink.SinkFunction;
import component.source.Source;
import common.*;
import org.apache.commons.cli.*;
import query.LiebreContext;
import query.Query;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class LinearProcessing {

    static double _LIDAR_CLOSE_RANGE = 2;
    static double _LIDAR_FAR_RANGE = 100;
    static double _GROUND = 2.3;
    static String INPUT_FILE;
    static String CONFIG_FILE;
    static String OUTPUT_FOLDER;
    static int _NUM_Scouts = 1;
    static double _EPSILON = 0.7;
    static int _MIN_POINTS = 10;
    static long _WS_SCOUT = 160000;
    static long _WA_SCOUT = 160000;
    static boolean metrics = false;
    static int _Injection_Rate = 20000;
    static boolean _Flow_Control = true;

    private static Options buildOptions() {
        Options options = new Options();
        options.addOption("h", "help", false, "Prints this help message");
        options.addOption("ns", "scouts", true, "Number of scouts to work in parallel");
        options.addOption("e", "epsilon", true, "epsilon");
        options.addOption("min", "minPoints", true, "Minimum number of points in a cluster.");
        options.addOption("d", "dataFile", true, "Path to the input data");
        options.addOption("c", "configFile", true, "Path to the configuration file");
        options.addOption("o", "output", true, "Output folder to store the results and statistics");
        options.addOption("m", "metrics", false, "keep the metrics for the operators");
        options.addOption("rate", "rate", true, "Injection rate in terms of number of tuples per milli sec");
        options.addOption("r", "report", true, "Reporting period in terms of number of tuples");
        options.addOption("f", "flow", false, "Enable flow control mechanism for scale gate");
        return options;
    }

    private static void updateParams(CommandLine cl, Options opts) throws ParseException {
        for (Iterator<Option> itr = cl.iterator(); itr.hasNext(); ) {
            Option opt = itr.next();

            switch (opt.getOpt()) {
                case "ns":
                    _NUM_Scouts = Integer.parseInt(opt.getValue());
                    break;
                case "e":
                    _EPSILON = Double.parseDouble(opt.getValue());
                    break;
                case "min":
                    _MIN_POINTS = Integer.parseInt(opt.getValue());
                    break;
                case "d":
                    INPUT_FILE = opt.getValue();
                    break;
                case "c":
                    CONFIG_FILE = opt.getValue();
                    break;
                case "o":
                    OUTPUT_FOLDER = opt.getValue();
                    break;
                case "m":
                    metrics = true;
                    break;
                case "rate":
                    _Injection_Rate = Integer.parseInt(opt.getValue());
                    break;
                case "r":
                    _WA_SCOUT = Long.parseLong(opt.getValue());
                    break;
                case "f":
                    _Flow_Control = true;
                    break;
                case "h":
                    help(opts);
                    System.exit(1);
                default:
                    help(opts);
                    throw new ParseException(opt.getOpt());
            }
        }
    }

    private static void help(Options opts) {
        HelpFormatter hf = new HelpFormatter();
        hf.printHelp("Join test", opts);
    }

    public static void main(String[] args) throws Exception {

        CommandLineParser parser = new DefaultParser();
        Options options = buildOptions();
        CommandLine cl = null;

        try {
            cl = parser.parse(options, args);
        } catch (ParseException e1) {
            help(options);
            e1.printStackTrace();
        }
        assert cl != null;
        updateParams(cl, options);

        if (metrics) {
            String metricsFolder = OUTPUT_FOLDER + "/metrics";
            LiebreContext.setOperatorMetrics(Metrics.file(metricsFolder));
            LiebreContext.setStreamMetrics(Metrics.file(metricsFolder, true));
            LiebreContext.setUserMetrics(Metrics.file(metricsFolder));
        }

        Configurations config = new Configurations(_EPSILON, _MIN_POINTS, _NUM_Scouts, CONFIG_FILE, OUTPUT_FOLDER);
        Graph graph = new Graph(config);

        Query q = new Query();

        Source<String> inputStream = q.addTextFileSource("input", INPUT_FILE);

        Operator<String, LidarPoint> lidarData = q.addFlatMapOperator("map", new FlatMapFunction<String, LidarPoint>() {
            int laser = 0;
            int step = 0;
            int rotation = 0;
            long counter = 0;
            long prevStartCounter = 0;

            long tupleCounter = 0;
            long startTime = 0;
            long prevTime = 0;

            final PrintWriter writer = new PrintWriter(new FileWriter(OUTPUT_FOLDER + "/rate.txt"), true);

            @Override
            public List<LidarPoint> apply(String line) {
                List<LidarPoint> result = new LinkedList<>();

                String[] tokens = line.split(",");
                if (tokens.length == 1) {
                    laser = 0;
                    step = 0;

                    if (rotation == 0) {
                        startTime = System.nanoTime();
                        prevTime = startTime;
                        counter = _WS_SCOUT;
                        prevStartCounter = counter;
                        rotation++;
                        return null;
                    }

                    long avg = tupleCounter * 1000000000 / (System.nanoTime() - startTime);
                    writer.println(avg);
                    startTime = System.nanoTime();
                    tupleCounter = 0;

                    counter = prevStartCounter + _WS_SCOUT;
                    result.add(new LidarPoint(System.nanoTime(), counter - 1, Tuple.TupleT.DUMMY, 0, 0, 0, laser, step, laser + (step * config.NUM_OF_LASERS), -1));
                    result.add(new LidarPoint(System.nanoTime(), counter, Tuple.TupleT.FLUSH, 0, 0, 0, -1, -1, -1, -1));

                    counter += _WA_SCOUT;
                    result.add(new LidarPoint(System.nanoTime(), counter, Tuple.TupleT.FLUSH, 0, 0, 0, -1, -1, -1, -1));

                    counter += _WA_SCOUT;
                    prevStartCounter = counter;
                    rotation++;
                    return result;
                }

                if (!_Flow_Control && tupleCounter % _Injection_Rate == 0) {
                    long diff = (System.nanoTime() - prevTime) / 1000000;
                    if (diff < 1000)
                        Util.sleep((1000 - diff));
                    prevTime = System.nanoTime();
                }

                tupleCounter++;

                result.add(new LidarPoint(System.nanoTime(), counter, Tuple.TupleT.NORMAL, Double.parseDouble(tokens[0]),
                        Double.parseDouble(tokens[1]), Double.parseDouble(tokens[2]), laser, step,
                        laser + (step * config.NUM_OF_LASERS), laser % config.NUM_OF_SCOUTS));

                counter++;
                laser++;
                if (laser == config.NUM_OF_LASERS) {
                    laser = 0;
                    step++;
                }

                if ((counter + 1) % _WA_SCOUT == 0)
                    result.add(new LidarPoint(System.nanoTime(), counter, Tuple.TupleT.DUMMY, 0, 0, 0, laser, step, laser + (step * config.NUM_OF_LASERS), -1));

                return result;
            }
        });

        Operator<LidarPoint, LidarPoint> filteredData = q.addFilterOperator("filter", (FilterFunction<LidarPoint>) point -> {

            if (point.isDummy() || point.isFlush())
                return true;
            return !(-1 * point.z > _GROUND) && !(point.dist < _LIDAR_CLOSE_RANGE) && !(point.dist > _LIDAR_FAR_RANGE);
        });

        List<Operator<LidarPoint, Clusters>> scouts = q.addTimeAggregateOperator("scout", _NUM_Scouts, _WS_SCOUT,
                _WA_SCOUT, new PiLisco(config, graph));

        Sink<Clusters> sink = q.addBaseSink("sink", new SinkFunction<Clusters>() {

            final PrintWriter writer = new PrintWriter(new FileWriter(OUTPUT_FOLDER + "/latency.txt"), true);

            @Override
            public void accept(Clusters clusters) {
                if (!clusters.isFlush())
                    writer.println(clusters.getStimulus());
            }
        });

        q.connect(inputStream, lidarData).connect(lidarData, filteredData).connect(Arrays.asList(filteredData), scouts, _Flow_Control)
                .connect(scouts, Arrays.asList(sink), false);

        q.activate();

    }
}
