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
    //Integer 400-411:  unit counters
    //Integer 412:  radio unit ID
    //Integer 413:  last active radio unit ID

    //Info: X (0-9) Y (10-19) Type (20-22) Timestamp (23-31)

    static RobotController rc;
    static int myId;
    static int myType;
    static int frame = -1;
    static int myRadioID = -1;
    static Counter[] allyCounters = new Counter[6];
    static Counter[] underConstructionCounters = new Counter[6];

    static int[] allyCounts = new int[6];
    static int[] buildees = new int[2];



    public Radio(RobotController rc_) {
        rc = rc_;
        myType = typeToInt(rc.getType());
        Counter.rc = rc_;
        Stream.rc = rc_;
        try {
            myRadioID = rc.readBroadcast(412);
            rc.broadcast(412, myRadioID + 1);
        } catch (Exception ex) {
            throw new RuntimeException("Counters not c'tored correctly");
        }

        if (rc.getType() == RobotType.ARCHON) {
            myId = getArchonCounter() + 1;
            //incrementArchonCounter();
        } else {
            // int frame = rc.getRoundNum();
            // int uc = getUnitCounter() + 4;
            // int id = -1;
            // for (int pos = 4; pos < uc; pos++) {
                // if (frame - getUnitAge(pos) > 40) {
                    // id = pos;
                    // break;
                // }
            // }
            // if (id > 0) {
                // myId = id;
                // System.out.println("Reused unit slot " + myId + " / " + uc);
            // } else {
                // myId = getUnitCounter() + 4;
                // //incrementUnitCounter();
            // }
            /*System.out.println("Scouts: " + countAllies(RobotType.SCOUT));
            System.out.println("Soldiers: " + countAllies(RobotType.SOLDIER));
            System.out.println("Lumberjacks: " + countAllies(RobotType.LUMBERJACK));
            System.out.println("Gardeners: " + countAllies(RobotType.GARDENER));*/
        }



        reportMyPosition(rc.getLocation());
    }

    public void reportMyPosition(MapLocation location) {
        int info = ((int) location.x << 22) | ((int) location.y << 12) | (myType << 9) | (rc.getRoundNum() / 8);
        write(myId, info);
    }

    public static int countAllies(RobotType robotType) {
        return allyCounts[typeToInt(robotType)];
    }

    public int[] countAllies() {
        return allyCounts;
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

    public static void keepAlive() throws GameActionException {
        // System.out.println("before: " + Clock.getBytecodeNum());
        // Check if it's the first unit to call keepAlive this round
        int previousRoundNum = rc.readBroadcast(413);
        if (previousRoundNum < frame) {
            rc.broadcast(413, frame);
            // System.out.println("I am first");
            allyCounts[0] = Counter.commit(400);
            allyCounts[1] = Counter.commit(401);
            allyCounts[2] = Counter.commit(402);
            allyCounts[3] = Counter.commit(403);
            allyCounts[4] = Counter.commit(404);
            allyCounts[5] = Counter.commit(405);

            allyCounts[myType] = Counter.increment(400 + myType);

            allyCounts[0] += Counter.commit(406);
            allyCounts[1] += Counter.commit(407);
            allyCounts[2] += Counter.commit(408);
            allyCounts[3] += Counter.commit(409);
            allyCounts[4] += Counter.commit(410);
            allyCounts[5] += Counter.commit(411);
        } else {
            allyCounts[0] = Counter.get(400);
            allyCounts[1] = Counter.get(401);
            allyCounts[2] = Counter.get(402);
            allyCounts[3] = Counter.get(403);
            allyCounts[4] = Counter.get(404);
            allyCounts[5] = Counter.get(405);

            allyCounts[myType] = Counter.increment(400 + myType);

            allyCounts[0] += Counter.get(406);
            allyCounts[1] += Counter.get(407);
            allyCounts[2] += Counter.get(408);
            allyCounts[3] += Counter.get(409);
            allyCounts[4] += Counter.get(410);
            allyCounts[5] += Counter.get(411);
        }

        // Only gardeners have buildees
        if (myType == 1) {
            if (frame <= (buildees[0] & 0xFFFF)) {
                int robotType = buildees[0] >> 16;
                Counter.increment(406 + robotType);
            }

            if (frame <= (buildees[1] & 0xFFFF)) {
                int robotType = buildees[1] >> 16;
                Counter.increment(406 + robotType);
            }
        }

        // System.out.println("Scouts: " + countAllies(RobotType.SCOUT));
        // System.out.println("Soldiers: " + countAllies(RobotType.SOLDIER));
        // System.out.println("Lumberjacks: " + countAllies(RobotType.LUMBERJACK));
        // System.out.println("Gardeners: " + countAllies(RobotType.GARDENER));
        // System.out.println("after: " + Clock.getBytecodeNum());
    }

    public static void reportBuild(RobotType robotType) throws GameActionException {
        int index = typeToInt(robotType);
        Counter.increment(406 + index);

        // gardeners activate immediately
        if (robotType != RobotType.GARDENER) {
            buildees[frame < (buildees[0] & 0xFFFF) ? 1 : 0] = (frame + 20) + (index << 16);
        }
    }

    public void reportEnemy(MapLocation location, RobotType type, int time) {
        int info = ((int) Math.round(location.x) << 22) | ((int) Math.round(location.y) << 12) | (typeToInt(type) << 9) | (time / 8);
        write(getEnemyCounter() + 101, info);
        //System.out.println("Reported enemy #" + (getEnemyCounter() + 101) + " at " + location + " age " + (rc.getRoundNum() - getUnitAge(getEnemyCounter() + 101)));
        //incrementEnemyCounter();
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


    private static int cache[] = new int[GameConstants.BROADCAST_MAX_CHANNELS];
    private static int cacheAge[] = new int[GameConstants.BROADCAST_MAX_CHANNELS];

    static int read(int pos) {
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

    static void write(int pos, int value) {
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
            case ARCHON:
                return 0;
            case GARDENER:
                return 1;
            case LUMBERJACK:
                return 2;
            case SOLDIER:
                return 3;
            case TANK:
                return 4;
            case SCOUT:
                return 5;
        }
        throw new RuntimeException("Welp");
    }

    static RobotType intToType(int num) {
        switch (num) {
            case 0:
                return RobotType.ARCHON;
            case 1:
                return RobotType.GARDENER;
            case 2:
                return RobotType.LUMBERJACK;
            case 3:
                return RobotType.SOLDIER;
            case 4:
                return RobotType.TANK;
            case 5:
                return RobotType.SCOUT;
        }
        return null;
    }


}
