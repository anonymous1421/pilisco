package common;

import common.tuple.BaseRichTuple;

public class Tuple extends BaseRichTuple {
    public enum TupleT {DUMMY, NORMAL, FLUSH}

    protected final TupleT type;

    public Tuple(long stimulus, long timestamp, String key, TupleT type, int injectorID) {
        super(stimulus, timestamp, key, injectorID);
        this.type = type;
    }

    public TupleT getType() {
        return type;
    }

    public boolean isDummy() {
        return type == TupleT.DUMMY;
    }

    public boolean isFlush() {
        return type == TupleT.FLUSH;
    }
}

