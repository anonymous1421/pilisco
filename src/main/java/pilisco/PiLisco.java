package pilisco;

import component.operator.in1.aggregate.BaseTimeWindowAddSlide;
import common.*;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class PiLisco extends BaseTimeWindowAddSlide<LidarPoint, Clusters> {

    private final Graph graph;
    private int laserIndex = 0, stepIndex = 0;
    private final Configurations config;
    private boolean flag = true;
    private long dummyTime = 0;

    private long numberOfComp;
    private long tupleCounter;
    private long processTime;

    private final HashSet<Integer> visited = new HashSet<>();

    public PiLisco(Configurations config, Graph graph) {
        this.config = config;
        this.graph = graph;
        numberOfComp = 0;
        tupleCounter = 0;
        processTime = 0;
    }

    @Override
    public BaseTimeWindowAddSlide<LidarPoint, Clusters> factory() {
        return new PiLisco(config, graph);
    }

    @Override
    public void add(LidarPoint nodeToAdd) {
        long startProcess = System.nanoTime();
        if (nodeToAdd.isFlush()) {
            flag = false;
            return;
        }
        flag = true;

        clearExtraPoints(nodeToAdd);
        if (nodeToAdd.isDummy()) {
            dummyTime = nodeToAdd.getStimulus();
            return;
        }

        graph.updatePoint(instanceNumber, nodeToAdd);

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
                        if (EuclideanDistancePow(nodeToAdd, node) <= config.EPSILON_POW) {
                            graph.updateEdge(node.id, nodeToAdd.id, nodeToAdd.getTimestamp());
                        }
                    }
                }
            }
        }


        if (nodeToAdd.storingThreadId == instanceNumber) {
            graph.updatedPoints.add(nodeToAdd.id);
            graph.removeExtraEdges(nodeToAdd.id, nodeToAdd.getTimestamp());
        }

        laserIndex++;
        if (laserIndex == config.NUM_OF_LASERS) {
            laserIndex = 0;
            stepIndex++;
            if (stepIndex == config.NUM_OF_STEPS)
                stepIndex = 0;
        }

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

                    traverse();
                    Clusters tuple = new Clusters((System.nanoTime() - dummyTime) / 1000000, System.nanoTime(), Tuple.TupleT.NORMAL, graph.clusters, instanceNumber);
                    graph.barrier.set(config.NUM_OF_SCOUTS);
                    return tuple;
                }
            } while (!graph.barrier.compareAndSet(val, val - 1));

            while (graph.barrier.get() != config.NUM_OF_SCOUTS) ;

        }
        return new Clusters(System.nanoTime(), System.nanoTime(), Tuple.TupleT.FLUSH, graph.clusters, instanceNumber);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public void slideTo(long startTimestamp) { }

    private void clearExtraPoints(LidarPoint node) {
        while (stepIndex != node.stepNum || laserIndex != node.laserNum) {
            int id = graph.removePoint(instanceNumber, laserIndex, stepIndex);
            if (id != -1) {
                graph.removeVertex(id, node.getTimestamp());
                graph.updatedPoints.add(id);
            }
            laserIndex++;
            if (laserIndex == config.NUM_OF_LASERS) {
                laserIndex = 0;
                stepIndex++;
                if (stepIndex == config.NUM_OF_STEPS)
                    stepIndex = 0;
            }
        }
    }

    private double EuclideanDistancePow(LidarPoint p1, LidarPoint p2) {
        return Math.pow((p1.x - p2.x), 2) + Math.pow((p1.y - p2.y), 2) + Math.pow((p1.z - p2.z), 2);
    }

    private void traverse() {
        visited.clear();
        while (!graph.updatedPoints.isEmpty()) {
            int p = graph.updatedPoints.poll();
            if (graph.heads[p] != 0 || graph.hasNeighbours(p))
                if (!visited.contains(p)) {
                    int id = graph.heads[p];
                    if (id != 0) {
                        HashSet<Integer> tmp = graph.clusters.get(id);
                        if (tmp != null && !tmp.isEmpty())
                            graph.updatedPoints.addAll(graph.clusters.get(id));
                        graph.clusters.remove(id);
                    }
                    HashSet<Integer> members = BFS(p, id);
                    if (members.size() >= config.MIN_POINTS) {
                        for (int ind : members)
                            graph.heads[ind] = graph.clusterId;
                        graph.clusters.put(graph.clusterId++, members);
                    }
                }
        }
    }

    private HashSet<Integer> BFS(int ind, int clusterToRemove) {
        HashSet<Integer> members = new HashSet<>();
        LinkedList<Integer> queue = new LinkedList<>();

        visited.add(ind);
        queue.add(ind);

        while (queue.size() != 0) {
            ind = queue.poll();
            members.add(ind);
            int id = graph.heads[ind];
            if (id != 0 && id != clusterToRemove) {
                HashSet<Integer> tmp = graph.clusters.get(id);
                if (tmp != null && !tmp.isEmpty())
                    graph.updatedPoints.addAll(graph.clusters.get(id));
                graph.clusters.remove(id);
            }

            List<Integer> neighbours = graph.getAdjVertices(ind);
            for (int n : neighbours) {
                if (!visited.contains(n)) {
                    visited.add(n);
                    queue.add(n);
                }
            }
        }
        return members;
    }

    private class NeighbourMask {
        private final int minLaser, maxLaser, minStep, maxStep;
        private final double minDist, maxDist;

        public NeighbourMask(LidarPoint node) {
            minDist = Math.max(node.dist - config.EPSILON, 0);
            maxDist = node.dist + config.EPSILON;
            minStep = node.stepNum - config.getNumOfStepsToProcess(node.dist);
            maxStep = node.stepNum;//+ config.getNumOfStepsToProcess(node.dist);
            minLaser = Math.max(node.laserNum - config.getNumOfLasersToProcess(node.dist), 0);
            maxLaser = Math.min(node.laserNum + config.getNumOfLasersToProcess(node.dist), config.NUM_OF_LASERS - 1);
        }
    }
}