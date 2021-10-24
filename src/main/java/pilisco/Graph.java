package pilisco;

import common.Configurations;
import common.LidarPoint;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

public class Graph {
    final AtomicInteger barrier;
    final ConcurrentLinkedQueue<Integer> updatedPoints;
    private final AtomicReference<Edge>[][] adjList;
    private final AtomicIntegerArray edgeNumbers;
    private final LidarPoint[][] tuples;
    private final int maximumNumOfNeighbours = 500;

    int clusterId = 1;
    final HashMap<Integer, HashSet<Integer>> clusters = new HashMap<>();
    final int[] heads;

    public Graph(Configurations config) {
        tuples = new LidarPoint[config.NUM_OF_LASERS][config.NUM_OF_STEPS];

        barrier = new AtomicInteger(config.NUM_OF_SCOUTS);

        updatedPoints = new ConcurrentLinkedQueue<>();

        int numOfPoints = config.NUM_OF_LASERS * config.NUM_OF_STEPS;
        adjList = new AtomicReference[numOfPoints][maximumNumOfNeighbours];
        edgeNumbers = new AtomicIntegerArray(numOfPoints);
        for (int i = 0; i < numOfPoints; i++) {
            edgeNumbers.set(i, 0);
            for (int j = 0; j < maximumNumOfNeighbours; j++)
                adjList[i][j] = new AtomicReference<>(new Edge());
        }

        heads = new int[config.NUM_OF_LASERS * config.NUM_OF_STEPS];
    }

    public void updateEdge(int vertexID, int adjVertexID, long ts) {
        addOrUpdate(vertexID, adjVertexID, ts);
        addOrUpdate(adjVertexID, vertexID, ts);
    }

    public void addOrUpdate(int vertexID, int adjVertexID, long ts) {
        int firstEmpty = -1, count = 0;
        for (int i = 0; i < maximumNumOfNeighbours && count < edgeNumbers.get(vertexID); i++) {
            Edge edge = adjList[vertexID][i].updateAndGet(value -> {
                if (value.adjVertexID == adjVertexID && value.ts < ts)
                    value.update(ts, adjVertexID);
                return value;
            });
            if (edge.adjVertexID == adjVertexID)
                return;
            if (edge.adjVertexID != -1)
                count++;
            else if (firstEmpty == -1)
                firstEmpty = i;
        }
        if (firstEmpty != -1)
            addEdge(vertexID, adjVertexID, ts, firstEmpty);
        else
            addEdge(vertexID, adjVertexID, ts, count);
    }

    private void addEdge(int vertexID, int adjVertexID, long ts, int startInd) {
        for (int i = startInd; i < maximumNumOfNeighbours; i++) {
            Edge edge = adjList[vertexID][i].updateAndGet(value -> {
                if (value.adjVertexID == -1)
                    value.update(ts, adjVertexID);
                return value;
            });
            if (edge.adjVertexID == adjVertexID) {
                edgeNumbers.getAndIncrement(vertexID);
                return;
            }
        }
    }

    public void removeVertex(int vertexID, long ts) {
        for (int i = 0, count = 0; i < maximumNumOfNeighbours && count < edgeNumbers.get(vertexID); i++) {
            Edge edge = adjList[vertexID][i].get();
            int adjVertexID = edge.adjVertexID;
            if (adjVertexID != -1) {
                count++;
                adjList[vertexID][i].updateAndGet(value -> {
                    if (value.adjVertexID != -1)
                        value.update(ts, -1);
                    return value;
                });
                removeEdge(adjVertexID, vertexID, ts);
            }
        }
        edgeNumbers.set(vertexID, 0);
    }

    private void removeEdge(int vertexID, int adjVertexID, long ts) {
        boolean flag;
        for (int i = 0, count = 0; i < maximumNumOfNeighbours && count < edgeNumbers.get(vertexID); i++) {
            Edge edge = adjList[vertexID][i].get();
            if (edge.adjVertexID != -1)
                count++;
            flag = edge.adjVertexID == adjVertexID;
            edge = adjList[vertexID][i].updateAndGet(value -> {
                if (value.adjVertexID == adjVertexID && value.ts < ts)
                    value.update(ts, -1);
                return value;
            });
            if (flag) {
                if (edge.adjVertexID == -1)
                    edgeNumbers.decrementAndGet(vertexID);
                break;
            }
        }
    }

    public void removeExtraEdges(int vertexID, long ts) {
        boolean flag;
        int numRemovedEdges = 0;
        int totalEdges = edgeNumbers.get(vertexID);
        for (int i = 0, count = 0; i < maximumNumOfNeighbours && count < totalEdges; i++) {
            Edge edge = adjList[vertexID][i].get();
            if (edge.adjVertexID != -1)
                count++;
            int adjVertexID = edge.adjVertexID;
            flag = adjVertexID != -1 && edge.ts < ts;
            adjList[vertexID][i].updateAndGet(value -> {
                if (value.adjVertexID != -1 && value.ts < ts)
                    value.update(ts, -1);
                return value;
            });
            if (flag) {
                numRemovedEdges++;
                removeEdge(adjVertexID, vertexID, ts);
            }
        }
        edgeNumbers.compareAndSet(vertexID, totalEdges, (totalEdges - numRemovedEdges));
    }

    public List<Integer> getAdjVertices(int vertexID) {
        List<Integer> edgeList = new LinkedList<>();
        for (int i = 0; i < maximumNumOfNeighbours; i++) {
            Edge edge = adjList[vertexID][i].get();
            if (edge.ts == 0)
                break;
            if (edge.adjVertexID != -1)
                edgeList.add(edge.adjVertexID);
        }
        return edgeList;
    }

    public boolean hasNeighbours(int vertexID) {
        boolean flag = false;
        for (int i = 0; i < maximumNumOfNeighbours && !flag; i++) {
            Edge edge = adjList[vertexID][i].get();
            if (edge.ts == 0)
                break;
            if (edge.adjVertexID != -1)
                flag = true;
        }
        return flag;
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

    public int removePoint(int threadId, int laser, int step) {
        int val = -1;
        if (tuples[laser][step] != null && tuples[laser][step].storingThreadId == threadId) {
            val = tuples[laser][step].id;
            tuples[laser][step] = null;
        }
        return val;
    }
}
