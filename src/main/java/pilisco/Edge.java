package pilisco;

public class Edge {
    long ts;
    int adjVertexID;

    public Edge() {
        ts = 0;
        adjVertexID = -1;
    }

    public void update(long ts, int adjVertexID) {
        this.ts = ts;
        this.adjVertexID = adjVertexID;
    }
}
