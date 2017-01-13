package johnny4;

import battlecode.common.*;

public class Counter {

    //Integer 0:        Archon Counter (0-7) Unit Counter (8-15) Enemy Counter (16-23)
    //Integer 1-3:      Archon Info
    //Integer 4-100:    Unit Info
    //Integer 101-200:  Enemy Info
    //Integer 201-300:  Enemy Trees
    //Integer 301-320:  Requested trees to remove according to priority
    //Integer 321:      ALARM ALARM

    //Info: X (0-9) Y (10-19) Type (20-22) Timestamp (23-31)

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
