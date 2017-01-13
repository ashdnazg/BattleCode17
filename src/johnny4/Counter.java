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
    int myIndex;
    int currentCount;

    public Counter(int index) throws GameActionException {
        this.myIndex = index;
        int value = rc.readBroadcast(myIndex);
        updateCount(value);
    }

    public int increment() throws GameActionException {
        int value = rc.readBroadcast(myIndex);
        value += 1;
        rc.broadcast(myIndex, value);
        updateCount(value);
        return currentCount;
    }

    public int decrement() throws GameActionException {
        int value = rc.readBroadcast(myIndex);
        value -= 1;
        rc.broadcast(myIndex, value);
        updateCount(value);
        return currentCount;
    }

    public int commit() throws GameActionException {
        int value = rc.readBroadcast(myIndex);
        currentCount = value & 0xFFFF;
        value <<= 16;
        rc.broadcast(myIndex, value);
        return currentCount;
    }

    public int commitAndIncrement() throws GameActionException {
        int value = rc.readBroadcast(myIndex);
        value <<= 16;
        value += 1;
        rc.broadcast(myIndex, value);
        updateCount(value);
        return currentCount;
    }

    public void updateCount(int value) throws GameActionException {
        currentCount = Math.max(value >> 16, value & 0xFFFF);
    }

    public int get() throws GameActionException {
        int value = rc.readBroadcast(myIndex);
        updateCount(value);
        return currentCount;
    }
}
