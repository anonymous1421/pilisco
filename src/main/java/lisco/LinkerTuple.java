package lisco;

import common.LidarPoint;
import common.Tuple;

import java.util.LinkedList;
import java.util.List;

public class LinkerTuple extends Tuple {
    final int pointID;
    final List<Integer> neighbours;

    public LinkerTuple(LidarPoint point, int injectorID) {
        super(point.getStimulus(), point.getTimestamp(), "", point.getType(), injectorID);
        pointID = -1;
        neighbours = null;
    }

    public LinkerTuple(LidarPoint point, List<Integer> neighbours, int injectorID) {
        super(point.getStimulus(), point.getTimestamp(), "", point.getType(), injectorID);
        pointID = point.id;
        this.neighbours = new LinkedList<>(neighbours);
    }

    @Override
    public String toString() {
        return "LinkerTuple{" +
                "pointID=" + pointID +
                ", neighbours=" + neighbours +
                '}';
    }
}
