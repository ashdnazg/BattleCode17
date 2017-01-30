package johnny4_20170130;

import battlecode.common.*;



// This counter stores the previous round's value in the upper 16 bits
// and the current value in the lower 16 bits.
// commit() is called in the start of each round.
public class Counter {

    static RobotController rc;

    public static void increment(int index) throws GameActionException {
        int value = rc.readBroadcast(index);
        value += 1;
        rc.broadcast(index, value);
    }

    public static void decrement(int index) throws GameActionException {
        int value = rc.readBroadcast(index);
        value -= 1;
        rc.broadcast(index, value);
    }

    public static int commit(int index) throws GameActionException {
        int value = rc.readBroadcast(index);
        value <<= 16;
        rc.broadcast(index, value);
        return value >> 16;
    }

    public static int get(int index) throws GameActionException {
        int value = rc.readBroadcast(index);
        return Math.max(value >> 16, value & 0xFFFF);
    }
}
