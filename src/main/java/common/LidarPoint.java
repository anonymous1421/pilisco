package common;

public class LidarPoint extends Tuple {
    public final int id;
    public final int storingThreadId;
    public final int laserNum, stepNum;
    public double x, y, z;
    public double alpha, theta, dist;

    public LidarPoint(long systemTime, long ts, TupleT type, double dist, double theta,
                      double alpha, int laserNum, int stepNum, int id, int threadId) {
        super(systemTime, ts, "", type, 0);

        this.id = id;
        this.storingThreadId = threadId;

        this.dist = dist;
        this.theta = theta;
        this.alpha = alpha;

        this.laserNum = laserNum;
        this.stepNum = stepNum;

        this.x = dist * Math.cos(theta) * Math.sin(alpha);
        this.y = dist * Math.cos(theta) * Math.cos(alpha);
        this.z = dist * Math.sin(theta);
    }

    public void updatePoint(LidarPoint newPoint) {
        this.dist = newPoint.dist;
        this.theta = newPoint.theta;
        this.alpha = newPoint.alpha;

        this.x = newPoint.x;
        this.y = newPoint.y;
        this.z = newPoint.z;
    }

    @Override
    public String toString() {
        return "(" + laserNum + "," + stepNum + ") -> " + dist;
    }
}
