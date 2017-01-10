package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Radio {

    //Integer 0:        Archon Counter (0-7) Unit Counter (8-15) Enemy Counter (16-23)
    //Integer 1-3:      Archon Info
    //Integer 4-100:    Unit Info
    //Integer 101-200:  Enemy Info

    //Info: X (0-9) Y (10-19) Type (20-22) Timestamp (23-31)

    RobotController rc;

    public Radio(RobotController rc) {
        this.rc = rc;
    }

    public int getEnemyCounter() {
        return (read(0) & 0x0000FF00) >> 8;
    }

    public int getUnitCounter() {
        return (read(0) & 0x00FF0000) >> 16;
    }

    public int getArchonCounter() {
        return (read(0) & 0x00FF0000) >> 24;
    }

    public void incrementArchonCounter() {
        write(0, (((getArchonCounter() + 1) % 4) << 24) | (((getUnitCounter() + 0) % 96) << 16) | (((getEnemyCounter() + 0) % 100) << 8));
    }
    public void incrementUnitCounter() {
        write(0, (((getArchonCounter() + 0) % 4) << 24) | (((getUnitCounter() + 1) % 96) << 16) | (((getEnemyCounter() + 0) % 100) << 8));
    }
    public void incrementEnemyCounter() {
        write(0, (((getArchonCounter() + 0) % 4) << 24) | (((getUnitCounter() + 0) % 96) << 16) | (((getEnemyCounter() + 1) % 100) << 8));
    }


    public void reportEnemy(MapLocation location, RobotType type, int time) {
        int info = ((int)location.x << 22) | ((int)location.y << 12) | (typeToInt(type) << 9) | (time / 8);
        write(getEnemyCounter() + 101, info);
        incrementEnemyCounter();
    }

    private int cache[] = new int[GameConstants.BROADCAST_MAX_CHANNELS];
    private int cacheAge[] = new int[GameConstants.BROADCAST_MAX_CHANNELS];

    private int read(int pos) {
        if (cacheAge[pos] != rc.getRoundNum()) {
            try {
                cache[pos] = rc.readBroadcast(pos);
                cacheAge[pos] = rc.getRoundNum();
            } catch (Exception ex) {
                throw new RuntimeException("read failed");
            }
        }
        return cache[pos];
    }

    private void write(int pos, int value) {
        cache[pos] = value;
        cacheAge[pos] = rc.getRoundNum();
        try {
            rc.broadcast(pos, value);
        } catch (GameActionException e) {
            throw new RuntimeException("write failed");
        }

    }

    private int typeToInt(RobotType rt) {
        switch (rt) {
            case SCOUT:
                return 0;
            case ARCHON:
                return 1;
            case GARDENER:
                return 2;
            case LUMBERJACK:
                return 3;
            case SOLDIER:
                return 4;
            case TANK:
                return 5;
        }
        throw new RuntimeException("Welp");
    }

    private RobotType intToType(int num) {
        switch (num) {
            case 0:
                return RobotType.SCOUT;
            case 1:
                return RobotType.ARCHON;
            case 2:
                return RobotType.GARDENER;
            case 3:
                return RobotType.LUMBERJACK;
            case 4:
                return RobotType.SOLDIER;
            case 5:
                return RobotType.TANK;
        }
        throw new RuntimeException("Welp");
    }

}
