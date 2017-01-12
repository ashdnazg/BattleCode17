package johnny4;

import battlecode.common.*;

public class Radio {

    //Integer 0:        Archon Counter (0-7) Unit Counter (8-15) Enemy Counter (16-23)
    //Integer 1-3:      Archon Info
    //Integer 4-100:    Unit Info
    //Integer 101-200:  Enemy Info
    //Integer 201-300:  Enemy Trees
    //Integer 301-320:  Requested trees to remove according to priority
    //Integer 321:      ALARM ALARM

    //Info: X (0-9) Y (10-19) Type (20-22) Timestamp (23-31)

    RobotController rc;
    final int myId;
    final int myType;
    int frame = 0;

    public Radio(RobotController rc) {
        this.rc = rc;
        if (rc.getType() == RobotType.ARCHON) {
            myId = getArchonCounter() + 1;
            incrementArchonCounter();
        } else {
            int frame = rc.getRoundNum();
            int uc = getUnitCounter() + 4;
            int id = -1;
            for (int pos = 4; pos < uc; pos++) {
                if (frame - getUnitAge(pos) > 40) {
                    id = pos;
                    break;
                }
            }
            if (id > 0) {
                myId = id;
                System.out.println("Reused unit slot " + myId + " / " + uc);
            } else {
                myId = getUnitCounter() + 4;
                incrementUnitCounter();
            }
            /*System.out.println("Scouts: " + countAllies(RobotType.SCOUT));
            System.out.println("Soldiers: " + countAllies(RobotType.SOLDIER));
            System.out.println("Lumberjacks: " + countAllies(RobotType.LUMBERJACK));
            System.out.println("Gardeners: " + countAllies(RobotType.GARDENER));*/
        }
        myType = typeToInt(rc.getType());
        reportMyPosition(rc.getLocation());
    }

    public void reportMyPosition(MapLocation location) {
        int info = ((int) location.x << 22) | ((int) location.y << 12) | (myType << 9) | (rc.getRoundNum() / 8);
        write(myId, info);
    }

    public int countAllies(RobotType robotType) {
        int shiftedType = typeToInt(robotType) << 9;
        int found = 0;
        int frame = rc.getRoundNum();
        int uc = getUnitCounter() + 4;
        for (int pos = 1; pos < uc; pos++) {
            if (pos == myId) continue;
            if ((read(pos) & 0b00000000000000000000111000000000) == shiftedType && frame - getUnitAge(pos) < 20) {
                found++;
            }
        }
        return found;
    }

    public int[] countAllies() {
        int ret[] = new int[8];
        int frame = rc.getRoundNum();
        int uc = getUnitCounter() + 4;
        for (int pos = 1; pos < uc; pos++) {
            if (pos == myId) continue;
            if (frame - getUnitAge(pos) < 20) {
                ret[(read(pos) & 0b00000000000000000000111000000000) >> 9] ++;
            }
        }
        return ret;
    }

    //very expensive, use sparingly
    public MapLocation[] getAllyPositions(RobotType robotType) {
        int shiftedType = typeToInt(robotType) << 9;
        int found = 0;
        int frame = rc.getRoundNum();
        MapLocation[] result = new MapLocation[100];
        int uc = getUnitCounter() + 4;
        for (int pos = 1; pos < uc; pos++) {
            if (pos == myId) continue;
            if ((read(pos) & 0b00000000000000000000111000000000) == shiftedType && frame - getUnitAge(pos) < 20) {
                result[found++] = new MapLocation(getUnitX(pos), getUnitY(pos));
            }
        }
        return result;
    }

    //very expensive, use sparingly
    public MapLocation[] getAllyPositions() {
        int found = 0;
        int frame = rc.getRoundNum();
        MapLocation[] result = new MapLocation[100];
        int uc = getUnitCounter() + 4;
        for (int pos = 1; pos < uc; pos++) {
            if (pos == myId) continue;
            if (frame - getUnitAge(pos) < 20) {
                result[found++] = new MapLocation(getUnitX(pos), getUnitY(pos));
            }
        }
        return result;
    }

    public int getEnemyCounter() {
        return (read(0) & 0x0000FF00) >> 8;
    }

    public int getUnitCounter() {
        return (read(0) & 0x00FF0000) >> 16;
    }

    public int getArchonCounter() {
        return (read(0) & 0xFF000000) >> 24;
    }

    public void incrementArchonCounter() {
        write(0, (((getArchonCounter() + 1) % 4) << 24) | (((getUnitCounter() + 0) % 96) << 16) | (((getEnemyCounter() + 0) % 100) << 8));
        System.out.println("Archon counter is now " + getArchonCounter());
    }

    public void incrementUnitCounter() {
        if (getUnitCounter() == 95) return;
        write(0, (((getArchonCounter() + 0) % 4) << 24) | (((getUnitCounter() + 1) % 96) << 16) | (((getEnemyCounter() + 0) % 100) << 8));
        System.out.println("Unit counter is now " + getUnitCounter());
    }

    public void incrementEnemyCounter() {
        write(0, (((getArchonCounter() + 0) % 4) << 24) | (((getUnitCounter() + 0) % 96) << 16) | (((getEnemyCounter() + 1) % 100) << 8));
    }

    public void reportEnemy(MapLocation location, RobotType type, int time) {
        int info = ((int) Math.round(location.x) << 22) | ((int) Math.round(location.y) << 12) | (typeToInt(type) << 9) | (time / 8);
        write(getEnemyCounter() + 101, info);
        //System.out.println("Reported enemy #" + (getEnemyCounter() + 101) + " at " + location + " age " + (rc.getRoundNum() - getUnitAge(getEnemyCounter() + 101)));
        incrementEnemyCounter();
    }

    public float getUnitX(int pos) {
        return ((read(pos) & 0b11111111110000000000000000000000) >> 22);
    }

    public float getUnitY(int pos) {
        return ((read(pos) & 0b00000000001111111111000000000000) >> 12);
    }

    public float getUnitAge(int pos) {
        return ((read(pos) & 0b00000000000000000000000111111111)) * 8;
    }

    public RobotType getUnitType(int pos) {
        return intToType((read(pos) & 0b00000000000000000000111000000000) >> 9);
    }


    private int cache[] = new int[GameConstants.BROADCAST_MAX_CHANNELS];
    private int cacheAge[] = new int[GameConstants.BROADCAST_MAX_CHANNELS];

    int read(int pos) {
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

    void write(int pos, int value) {
        cache[pos] = value;
        cacheAge[pos] = rc.getRoundNum();
        try {
            rc.broadcast(pos, value);
        } catch (GameActionException e) {
            throw new RuntimeException("write failed");
        }

    }

    // returns the index, so you can mark it as cut later
    public boolean requestTreeCut(TreeInfo ti) {
        int index = rc.getType() == RobotType.ARCHON ? 301 : 304;
        int zero_index = -1;
        for (; index <= 320; ++index) {
            int data = read(index);
            if (data == 0) {
                zero_index = index;
            } else if (((data ^ ti.ID) & 0b00000000000000000000111111111111) == 0) {
                return true;
            }
        }
        if (zero_index < 0) {
            return false;
        }
        int info = ((int) Math.round(ti.location.x) << 22) | ((int) Math.round(ti.location.y) << 12) | (ti.ID & 0b00000000000000000000111111111111);
        write(zero_index, info);
        return true;
    }

    public MapLocation findTreeToCut() {
        for (int index = 301; index <= 320; ++index) {
            int data = read(index);
            if (data != 0) {
                return new MapLocation(getUnitX(index), getUnitY(index));
            }
        }
        return null;
    }

    public MapLocation findClosestTreeToCut(MapLocation myLocation) {
        MapLocation tree, closest = null;
        for (int index = 301; index <= 320; ++index) {
            int data = read(index);
            if (data != 0) {
                tree = new MapLocation(getUnitX(index), getUnitY(index));
                if (closest == null || closest.distanceTo(myLocation) > tree.distanceTo(myLocation)) {
                    closest = tree;
                }
            }
        }
        return closest;
    }

    public void reportTreeCut(MapLocation location) {
        int loc = ((int) location.x << 22) | ((int) location.y << 12);
        for (int index = 301; index <= 320; ++index) {
            int data = read(index);
            if (((data ^ loc) & 0b11111111111111111111000000000000) == 0) {
                write(index, 0);
            }
        }
    }

    public void setAlarm() {
        write(321, rc.getRoundNum());
    }

    public boolean getAlarm() {
        int lastAlarm = read(321);
        return lastAlarm != 0 && (rc.getRoundNum() - lastAlarm) < 10;
    }

    static int typeToInt(RobotType rt) {
        switch (rt) {
            case SCOUT:
                return 6;
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

    static RobotType intToType(int num) {
        switch (num) {
            case 6:
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
        return null;
    }


}
