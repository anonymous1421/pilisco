package lisco;

import common.Configurations;
import common.LidarPoint;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class OriginalGraph {
    public final AtomicInteger barrier;
    private final AtomicReferenceArray<Node> adjList;
    private LidarPoint[][] tuples;
    private final Configurations config;

    public OriginalGraph(Configurations config, int parallelism) {
        this.config = config;
        tuples = new LidarPoint[config.NUM_OF_LASERS][config.NUM_OF_STEPS];

        barrier = new AtomicInteger(config.NUM_OF_SCOUTS);

        adjList = new AtomicReferenceArray<Node>(parallelism);
        for (int i = 0; i < parallelism; i++)
            adjList.set(i, new Node());
    }

    public void add(int threadID, int vertexID, List<Integer> neighbours) {
        adjList.get(threadID).add(vertexID, neighbours);
    }

    public void clear() {
        tuples = new LidarPoint[config.NUM_OF_LASERS][config.NUM_OF_STEPS];
        for (int i = 0; i < adjList.length(); i++)
            adjList.get(i).clear();
    }

    public List<NestedNode> getNodes() {
        List<NestedNode> nodes = new LinkedList<>();
        for (int i = 0; i < adjList.length(); i++)
            nodes.addAll(adjList.get(i).list);
        return nodes;
    }

    public void updatePoint(int threadId, LidarPoint point) {
        if (point.storingThreadId == threadId) {
            if (tuples[point.laserNum][point.stepNum] == null)
                tuples[point.laserNum][point.stepNum] = point;
            else
                tuples[point.laserNum][point.stepNum].updatePoint(point);
        }
    }

    public LidarPoint getPoint(int laser, int step) {
        if (tuples[laser][step] != null)
            return tuples[laser][step];
        return null;
    }

    private static class Node {
        List<NestedNode> list;

        public Node() {
            list = new LinkedList<>();
        }

        public void add(int id, List<Integer> neighbours) {
            list.add(new NestedNode(id, neighbours));
        }

        public void clear() {
            list.clear();
        }
    }

    public static class NestedNode {
        int id;
        List<Integer> neighbours;

        public NestedNode(int id, List<Integer> neighbours) {
            this.id = id;
            this.neighbours = new LinkedList<>(neighbours);
        }
    }
}
