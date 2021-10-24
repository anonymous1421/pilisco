package lisco;

import component.operator.in1.aggregate.BaseTimeWindowAdd;
import common.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class OriginalLisco extends BaseTimeWindowAdd<LidarPoint, Clusters> {

    private final OriginalGraph graph;
    private final Configurations config;

    private boolean flag = true;
    private long dummyTime = 0;

    private long numberOfComp;
    private long tupleCounter;
    private long processTime;

    private int clusterId = 0;
    private final int[] head;
    private final HashMap<Integer, HashSet<Integer>> clusters = new HashMap<>();

    private final HashSet<Integer> visited = new HashSet<>();

    public OriginalLisco(Configurations config, OriginalGraph graph) {
        this.graph = graph;
        this.config = config;

        numberOfComp = 0;
        tupleCounter = 0;
        processTime = 0;

        head = new int[config.NUM_OF_LASERS * config.NUM_OF_STEPS];
        for (int i = 0; i < config.NUM_OF_LASERS * config.NUM_OF_STEPS; i++)
            head[i] = -1;
    }

    @Override
    public BaseTimeWindowAdd<LidarPoint, Clusters> factory() {
        return new OriginalLisco(config, graph);
    }

    @Override
    public void add(LidarPoint nodeToAdd) {
        long startProcess = System.nanoTime();
        if (nodeToAdd.isFlush()) {
            flag = false;
            return;
        }
        flag = true;

        if (nodeToAdd.isDummy()) {
            dummyTime = nodeToAdd.getStimulus();
            return;
        }

        graph.updatePoint(instanceNumber, nodeToAdd);
        List<Integer> neighbours = new LinkedList<>();

        NeighbourMask mask = new NeighbourMask(nodeToAdd);
        for (int i = mask.minLaser; i <= mask.maxLaser; i++) {
            if (i % parallelismDegree == instanceNumber) {
                int extra = config.getExtraSteps(i, nodeToAdd.laserNum);
                for (int j = mask.minStep - extra; j <= mask.maxStep; j++) {
                    int step = (config.NUM_OF_STEPS + j) % config.NUM_OF_STEPS;
                    LidarPoint node = graph.getPoint(i, step);
                    if (node != null && node.id != nodeToAdd.id && node.dist >= mask.minDist
                            && node.dist <= mask.maxDist) {
                        numberOfComp++;
                        if (EuclideanDistancePow(nodeToAdd, node) <= config.EPSILON_POW)
                            neighbours.add(node.id);
                    }
                }
            }
        }

        if(!neighbours.isEmpty())
            graph.add(instanceNumber, nodeToAdd.id, neighbours);

        processTime += System.nanoTime() - startProcess;
        tupleCounter++;
    }

    @Override
    public Clusters getAggregatedResult() {
        if (flag) {
            int val;
            do {
                val = graph.barrier.get();
                if (val == 1) {
                    config.writer_comp.println(numberOfComp);
                    config.writer_time.println(tupleCounter + "," + (processTime / tupleCounter) / 1000);
                    numberOfComp = 0;
                    tupleCounter = 0;
                    processTime = 0;

                    List<OriginalGraph.NestedNode> nodes = graph.getNodes();
                    for(OriginalGraph.NestedNode node:nodes){
                        List<Integer> neighbours = node.neighbours;
                        for(int neighbour:neighbours)
                            expandThis(node.id, neighbour);
                    }
                    graph.clear();

                    clusters.keySet().removeIf(o -> clusters.get(o).size() < config.MIN_POINTS);
                    Clusters tuple = new Clusters((System.nanoTime() - dummyTime) / 1000000, System.nanoTime(), Tuple.TupleT.NORMAL, clusters, instanceNumber);
                    graph.barrier.set(config.NUM_OF_SCOUTS);
                    return tuple;
                }
            } while (!graph.barrier.compareAndSet(val, val - 1));

            while (graph.barrier.get() != config.NUM_OF_SCOUTS) ;

        }
        return new Clusters(System.nanoTime(), System.nanoTime(), Tuple.TupleT.FLUSH, clusters, instanceNumber);
    }

    private void expandThis(int point, int neighbour) {
        if (head[point] == -1) {
            if (head[neighbour] == -1) {
                head[neighbour] = clusterId;
                head[point] = clusterId;
                HashSet<Integer> tmp = new HashSet<>();
                tmp.add(point);
                tmp.add(neighbour);
                clusters.put(clusterId++, tmp);
            } else {
                head[point] = head[neighbour];
                clusters.get(head[point]).add(point);
            }
        } else if (head[neighbour] == -1) {
            head[neighbour] = head[point];
            clusters.get(head[neighbour]).add(neighbour);
        } else if (head[point] != head[neighbour]) {
            if (clusters.get(head[point]).size() < clusters.get(head[neighbour]).size()) {
                int clusterToRemove = head[point];
                for (int member : clusters.get(clusterToRemove)) {
                    head[member] = head[neighbour];
                    clusters.get(head[neighbour]).add(member);
                }
                clusters.remove(clusterToRemove);
            } else {
                int clusterToRemove = head[neighbour];
                for (int member : clusters.get(clusterToRemove)) {
                    head[member] = head[point];
                    clusters.get(head[point]).add(member);
                }
                clusters.remove(clusterToRemove);
            }
        }
    }

    private double EuclideanDistancePow(LidarPoint p1, LidarPoint p2) {
        return Math.pow((p1.x - p2.x), 2) + Math.pow((p1.y - p2.y), 2) + Math.pow((p1.z - p2.z), 2);
    }

    private class NeighbourMask {
        private final int minLaser, maxLaser, minStep, maxStep;
        private final double minDist, maxDist;

        public NeighbourMask(LidarPoint node) {
            minDist = Math.max(node.dist - config.EPSILON, 0);
            maxDist = node.dist + config.EPSILON;
            minStep = node.stepNum - config.getNumOfStepsToProcess(node.dist);
            maxStep = node.stepNum;// + config.getNumOfStepsToProcess(node.dist);
            minLaser = Math.max(node.laserNum - config.getNumOfLasersToProcess(node.dist), 0);
            maxLaser = Math.min(node.laserNum + config.getNumOfLasersToProcess(node.dist), config.NUM_OF_LASERS - 1);
        }
    }
}
