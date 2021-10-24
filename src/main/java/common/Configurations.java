package common;

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class Configurations {
    public final String out_folder;
    public final int NUM_OF_LASERS, NUM_OF_STEPS, MIN_POINTS;
    public final double EPSILON, EPSILON_POW;
    public final int NUM_OF_SCOUTS;

    private final int[] processLasers = new int[120];
    private final int[] processSteps = new int[120];

    private final int[][] extraSteps;

    public final PrintWriter writer_comp;
    public final PrintWriter writer_time;

    public Configurations(double epsilon, int minPts, int numOfScouts, String configFile, String OUTPUT_FOLDER) throws IOException {
        out_folder = OUTPUT_FOLDER;
        writer_comp = new PrintWriter(new FileWriter(OUTPUT_FOLDER + "/comp.txt"), true);
        writer_time = new PrintWriter(new FileWriter(OUTPUT_FOLDER + "/time.txt"), true);

        double _min_yaw_difference = 0.0015;
        double _min_elevation_difference = 0.0059;

        NUM_OF_SCOUTS = numOfScouts;

        int tempLasers = 0;
        int tempSteps = 0;
        List<Double> _initial_yaw_offset = new LinkedList<>();
        try {
            Scanner reader = new Scanner(new File(configFile));
            while (reader.hasNextLine()) {
                String[] tokens = reader.nextLine().split(",");
                if (tempLasers == 0) {
                    tempLasers = Integer.parseInt(tokens[0]);
                    tempSteps = Integer.parseInt(tokens[1]);
                } else {
                    _initial_yaw_offset.add(Double.valueOf(tokens[1]));
                }
            }
            reader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        NUM_OF_LASERS = tempLasers;
        NUM_OF_STEPS = tempSteps;

        extraSteps = new int[NUM_OF_LASERS][NUM_OF_LASERS];
        for (int i = 0; i < NUM_OF_LASERS; i++) {
            for (int j = 0; j < NUM_OF_LASERS; j++) {
                if (i == j)
                    extraSteps[i][j] = 0;
                else
                    extraSteps[i][j] = (int) Math.abs(
                            Math.ceil((_initial_yaw_offset.get(i) - _initial_yaw_offset.get(j)) / _min_yaw_difference));
            }
        }

        EPSILON = epsilon;
        EPSILON_POW = Math.pow(epsilon, 2);
        MIN_POINTS = minPts;

        for (int i = 1; i < 120; i++) {
            processLasers[i] = (int) Math.ceil(Math.asin(epsilon / i) / _min_elevation_difference);
            processSteps[i] = (int) Math.ceil(Math.asin(epsilon / i) / _min_yaw_difference);
        }
    }

    public int getNumOfLasersToProcess(double dist) {
        return processLasers[(int) dist];
    }

    public int getNumOfStepsToProcess(double dist) {
        return processSteps[(int) dist];
    }

    public int getExtraSteps(int i, int j) {
        return extraSteps[i][j];
    }
}
