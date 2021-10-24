package common;

import java.util.*;

public class Clusters extends Tuple {

    private final HashMap<Integer, HashSet<Integer>> clusters = new HashMap<>();

    public Clusters(long systemTime, long ts, TupleT type) {
        super(systemTime, ts, "", type, 0);
    }

    public Clusters(long systemTime, long ts, TupleT type, HashMap<Integer, HashSet<Integer>> c, int injectorID) {
        super(systemTime, ts, "", type, injectorID);
        clusters.clear();
        clusters.putAll(c);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder("Clusters: \n ");
        int counter = 0;
        for (int key : clusters.keySet()) {
            s.append("Cluster").append(++counter).append(" -> ");
            for (Integer integer : clusters.get(key)) {
                s.append(integer).append(", ");
            }
            s.append("\n");
        }
        return s.toString();
    }

    public String toCompare(int minPoints, int numLasers, int numSteps) {
        int[] heads = new int[numLasers * numSteps];
        for (int i = 0; i < numLasers * numSteps; i++)
            heads[i] = -1;

        int counter = 0;
        for (int key : clusters.keySet()) {
            assert (clusters.get(key).size() >= minPoints);
            counter++;
            for (int ind : clusters.get(key)) {
                heads[ind] = key;
            }
        }

        StringBuilder s = new StringBuilder();
        for (int i = 0; i < numSteps * numLasers; i++) {
            if (i % numLasers == 0)
                s.append("---------------------\n");
            s.append(heads[i]).append(",");

        }

        System.out.println("number of clusters: " + counter);
        return s.toString();
    }

    public String toVisualize(int minPoints) {
        StringBuilder s = new StringBuilder();
        for (int key : clusters.keySet()) {
            if (clusters.get(key).size() >= minPoints) {
                Iterator<Integer> itr = clusters.get(key).iterator();
                s.append(itr.next());
                while (itr.hasNext()) {
                    s.append(",").append(itr.next());
                }
                s.append("\n");
            }
        }
        return s.toString();
    }

    public String sortedComparison() {
        HashMap<Integer, List<Integer>> list = new HashMap<>();
        for (int key : clusters.keySet()) {
            ArrayList<Integer> tmp = new ArrayList(clusters.get(key));
            Collections.sort(tmp);
            list.put(tmp.get(0), tmp);
        }
        ArrayList<Integer> tmp = new ArrayList(list.keySet());
        Collections.sort(tmp);

        StringBuilder s = new StringBuilder();
        for (int key : tmp) {
            for (Integer integer : list.get(key)) {
                s.append(integer).append(", ");
            }
            s.append("\n");
        }
        return s.toString();
    }
}
