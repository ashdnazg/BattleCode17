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
    static int myIndex;
    static int value = 0;
    static int currentCount;
    static int frame = -1;

    public Counter(RobotController rc_, int index) throws GameActionException {
        myIndex = index;
        rc = rc_;
        frame = rc.getRoundNum();
        value = rc.readBroadcast(myIndex);
        updateCount();
    }

    public int initialIncrement() throws GameActionException {
        value += 0x00010001;
        rc.broadcast(myIndex, value);
        updateCount();
        return currentCount;
    }

    public int increment() throws GameActionException {
        int currentFrame = rc.getRoundNum();
        if (currentFrame > frame) {
            frame = currentFrame;
            value = rc.readBroadcast(myIndex);
        }
        value += 1;
        rc.broadcast(myIndex, value);
        updateCount();
        return currentCount;
    }

    public int commit() throws GameActionException {
        value = rc.readBroadcast(myIndex);
        frame = rc.getRoundNum();
        currentCount = value & 0xFFFF;
        value <<= 16;
        rc.broadcast(myIndex, value);
        return currentCount;
    }

    public int commitAndIncrement() throws GameActionException {
        value = rc.readBroadcast(myIndex);
        frame = rc.getRoundNum();
        value <<= 16;
        value += 1;
        rc.broadcast(myIndex, value);
        updateCount();
        return currentCount;
    }

    public void updateCount() throws GameActionException {
        currentCount = Math.max(value >> 16, value & 0xFFFF);
    }

    public int get() throws GameActionException {
        int currentFrame = rc.getRoundNum();
        if (currentFrame > frame) {
            frame = currentFrame;
            value = rc.readBroadcast(myIndex);
            updateCount();
        }
        return currentCount;
    }
}
