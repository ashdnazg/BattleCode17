package johnny4;

import battlecode.common.*;

public class Counter {

    static RobotController rc;

    public static int increment(int index) throws GameActionException {
        int value = rc.readBroadcast(index);
        value += 1;
        rc.broadcast(index, value);
        return Math.max(value >> 16, value & 0xFFFF);
    }

    public static int decrement(int index) throws GameActionException {
        int value = rc.readBroadcast(index);
        value -= 1;
        rc.broadcast(index, value);
        return Math.max(value >> 16, value & 0xFFFF);
    }

    public static int commit(int index) throws GameActionException {
        int value = rc.readBroadcast(index);
        value <<= 16;
        rc.broadcast(index, value);
        return value >> 16;
    }

    public static int commitAndIncrement(int index) throws GameActionException {
        int value = rc.readBroadcast(index);
        value <<= 16;
        value += 1;
        rc.broadcast(index, value);
        return Math.max(value >> 16, 1);
    }

    public static int get(int index) throws GameActionException {
        int value = rc.readBroadcast(index);
        return Math.max(value >> 16, value & 0xFFFF);
    }
}
