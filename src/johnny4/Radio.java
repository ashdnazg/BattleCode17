package johnny4;

import battlecode.common.*;

public class Radio {

    //Integer 0:        Contact boolean
    //Integer 1:        Enemy Counter
    //Integer 2-199:  Enemy Info
    //Integer 201:  Enemy Reports count
    //Integer 202-300:  Enemy Reports
    //Integer 301:      Tree cutting requests counter
    //Integer 302-320:  Requested trees to remove according to priority
    //Integer 321:      ALARM ALARM
    //Integer 322:      Scout hunters
    //Integer 400-405:  active unit counters
    //Integer 406-411:  under construction unit counters
    //Integer 412:  radio unit ID
    //Integer 413:  last round - used to figure who the first unit in the round is.
    //Integer 414 - 419: enemy unit counters // updated by Archon only
    //Integer 420 - 425: reports bloom filter
    //Integer 426: active gardener counter
    //Integer 427: Enemy delete requests count
    //Integer 428 - 449: Enemy delete requests


    //Info: X (0-9) Y (10-19) Type (20-22) Timestamp (23-31)

    static RobotController rc;
    static int myID;
    static int myType;
    static int frame = -1;
    static int myRadioID = -1;
    static int byteCodeLimit;
    static Team myTeam;

    static int[] allyCounts = new int[6];
    static int[] buildees = new int[2];
    static int activeGardenersCount;
    static int scoutHuntersCount;

    static int[] enemyCounts = new int[6];
    static int[] reportBloom = new int[6];
    static int[] tempBloom = new int[6];

    static int[] deleteBloom = new int[3];

    // only used by Archon
    static int[][] enemyIDToPos;
    static int[] enemyPosToID;

    static boolean haveBeenFirst = false;
    static final int ENEMY_EVICTION_AGE = 128;


    public Radio(RobotController rc_) {
        rc = rc_;
        myType = typeToInt(rc.getType());
        Counter.rc = rc_;
        Stream.rc = rc_;
        byteCodeLimit = rc.getType().bytecodeLimit;
        myTeam = rc.getTeam();
        try {
            myRadioID = rc.readBroadcast(412);
            rc.broadcast(412, myRadioID + 1);
        } catch (Exception ex) {
            throw new RuntimeException("Counters not c'tored correctly");
        }
    }

    public static int countAllies(RobotType robotType) {
        return allyCounts[typeToInt(robotType)];
    }

    public static int[] countAllies() {
        return allyCounts;
    }

    public static int[] countEnemies() {
        return enemyCounts;
    }

    public static int countEnemies(RobotType robotType) {
        return enemyCounts[typeToInt(robotType)];
    }

    //very expensive, use sparingly

    public static int getEnemyCounter() throws GameActionException {
        return rc.readBroadcast(1);
    }


    public static void setEnemyCounter(int newCounter) throws GameActionException {
        rc.broadcast(1, newCounter);
    }


    public static void processDeleteRequests() throws GameActionException {
        deleteBloom[0] = 0;
        deleteBloom[1] = 0;
        deleteBloom[2] = 0;

        int numDeletes = rc.readBroadcast(427);
        int info, h1, n1, b1, h2, n2, b2;
        for (int i = 0; i < numDeletes; ++i) {
            info = rc.readBroadcast(i + 428);
            h1 = (info * 41 + 23) % 96;
            n1 = h1 / 32;
            b1 = 1 << (h1 % 32);
            h2 = (info * 97 + 67) % 96;
            n2 = h2 / 32;
            b2 = 1 << (h2 % 32);
            deleteBloom[n1] |= b1;
            deleteBloom[n2] |= b2;
        }

        rc.broadcast(427, 0);
    }

    public static void updateEnemyCounts() throws GameActionException {
        //if (Util.DEBUG) System.out.println("before updating enemy counts: " + Clock.getBytecodeNum() + "frame: " + rc.getRoundNum());
        if (!haveBeenFirst) {
            setEnemyCounter(0);
            haveBeenFirst = true;
            enemyIDToPos = new int[180][];
            enemyPosToID = new int[100];
        }
        //if (Util.DEBUG) System.out.println("updating counts last: " + last);
        enemyCounts[0] = 0;
        enemyCounts[1] = 0;
        enemyCounts[2] = 0;
        enemyCounts[3] = 0;
        enemyCounts[4] = 0;
        enemyCounts[5] = 0;

        reportBloom[0] = 0;
        reportBloom[1] = 0;
        reportBloom[2] = 0;
        reportBloom[3] = 0;
        reportBloom[4] = 0;
        reportBloom[5] = 0;

        int pos = 2;
        int last = Math.min(99, getEnemyCounter()) * 2 + 2;

        int report = rc.readBroadcast(pos);
        int reportFrame = rc.readBroadcast(pos + 1);
        boolean writeNeeded = false;
        int ID, ID_a, ID_b, age, info, h1, n1, b1, h2, n2, b2, h3, n3, b3, type;
        while (pos < last) {
            ID = enemyPosToID[pos - 2];
            //if (Util.DEBUG) System.out.println("checking for eviction ID: " + ID);
            ID_a = ID % 180;
            ID_b = ID / 180;
            age = frame - reportFrame;

            info = report >>> 4;
            h1 = (info * 41 + 23) % 96;
            n1 = h1 / 32;
            b1 = 1 << (h1 % 32);
            h2 = (info * 97 + 67) % 96;
            n2 = h2 / 32;
            b2 = 1 << (h2 % 32);
            // evict units after not seeing them for ENEMY_EVICTION_AGE rounds
            if ((age > ENEMY_EVICTION_AGE) || (((deleteBloom[n1] & b1) != 0) && ((deleteBloom[n2] & b2) != 0))) {
                if (Util.DEBUG) System.out.println("evicting from pos: " + pos);
                enemyIDToPos[ID_a][ID_b] = 0;
                writeNeeded = true;
                last -= 2;
                enemyPosToID[pos - 2] = enemyPosToID[last - 2];
                report = rc.readBroadcast(last);
                reportFrame = rc.readBroadcast(last + 1);

                continue;
            }
            type = (report & 0b00000000000000000000000000000111);
            enemyCounts[type]++;
            if (writeNeeded) {
                rc.broadcast(pos, report);
                rc.broadcast(pos + 1, reportFrame);
                enemyIDToPos[ID_a][ID_b] = pos;
                writeNeeded = false;
            }
            pos += 2;
            report = rc.readBroadcast(pos);
            reportFrame = rc.readBroadcast(pos + 1);
        }

        if (writeNeeded) {
            rc.broadcast(pos, report);
            rc.broadcast(pos + 1, reportFrame);
        }

        pos = 202;
        int lastReport = 202 + rc.readBroadcast(201);
        int infoPos;
        for (; pos < lastReport; pos += 2) {
            report = rc.readBroadcast(pos);
            //if (Util.DEBUG) System.out.println("reading new report from pos: " + pos + " info: " + report);
            ID = rc.readBroadcast(pos + 1);
            //if (Util.DEBUG) System.out.println("ID: " + ID + " type: " + ((report & 0b00000000000000000000000000000111)));
            ID_a = ID % 180;
            ID_b = ID / 180;
            if (enemyIDToPos[ID_a] == null) {
                if (last > 199 || (byteCodeLimit - Clock.getBytecodeNum() < 5000)) {
                    continue;
                }
                enemyIDToPos[ID_a] = new int[180];
            } else {
                infoPos = enemyIDToPos[ID_a][ID_b];
                if (infoPos > 0) {
                    rc.broadcast(infoPos, report);
                    rc.broadcast(infoPos + 1, frame - 1);
                    continue;
                }
            }
            if (last > 199) {
                continue;
            }
            enemyIDToPos[ID_a][ID_b] = last;
            enemyPosToID[last - 2] = ID;
            rc.broadcast(last++, report);
            rc.broadcast(last++, frame - 1);
            type = (report & 0b00000000000000000000000000000111);
            enemyCounts[type]++;
        }

        setEnemyCounter((last - 2) / 2);
        rc.broadcast(201, 0);


        rc.broadcast(414, enemyCounts[0]);
        rc.broadcast(415, enemyCounts[1]);
        rc.broadcast(416, enemyCounts[2]);
        rc.broadcast(417, enemyCounts[3]);
        rc.broadcast(418, enemyCounts[4]);
        rc.broadcast(419, enemyCounts[5]);

        if (Util.DEBUG) System.out.println("Enemy Archons: " + enemyCounts[0]);
        if (Util.DEBUG) System.out.println("Enemy Gardeners: " + enemyCounts[1]);
        if (Util.DEBUG) System.out.println("Enemy Lumberjacks: " + enemyCounts[2]);
        if (Util.DEBUG) System.out.println("Enemy Soldiers: " + enemyCounts[3]);
        if (Util.DEBUG) System.out.println("Enemy Tanks: " + enemyCounts[4]);
        if (Util.DEBUG) System.out.println("Enemy Scouts: " + enemyCounts[5]);


        rc.broadcast(420, 0);
        rc.broadcast(421, 0);
        rc.broadcast(422, 0);
        rc.broadcast(423, 0);
        rc.broadcast(424, 0);
        rc.broadcast(425, 0);

        //if (Util.DEBUG) System.out.println("after updating enemy counts: " + Clock.getBytecodeNum() + "frame: " + rc.getRoundNum());
    }


    public static void keepAlive() throws GameActionException {
        // if (Util.DEBUG) System.out.println("before: " + Clock.getBytecodeNum());
        // Check if it's the first unit to call keepAlive this round
        int previousRoundNum = rc.readBroadcast(413);
        if (previousRoundNum < frame) {
            rc.broadcast(413, frame);
            // if (Util.DEBUG) System.out.println("I am first");
            allyCounts[0] = Counter.commit(400) + Counter.commit(406);
            allyCounts[1] = Counter.commit(401) + Counter.commit(407);
            allyCounts[2] = Counter.commit(402) + Counter.commit(408);
            allyCounts[3] = Counter.commit(403) + Counter.commit(409);
            allyCounts[4] = Counter.commit(404) + Counter.commit(410);
            allyCounts[5] = Counter.commit(405) + Counter.commit(411);
            activeGardenersCount = Counter.commit(426);
            scoutHuntersCount = Counter.commit(322);

            processDeleteRequests();

            updateEnemyCounts();

            if (Util.DEBUG) System.out.println("Allied Scouts: " + countAllies(RobotType.SCOUT));
            if (Util.DEBUG) System.out.println("Allied Soldiers: " + countAllies(RobotType.SOLDIER));
            if (Util.DEBUG) System.out.println("Allied Lumberjacks: " + countAllies(RobotType.LUMBERJACK));
            if (Util.DEBUG) System.out.println("Allied Gardeners: " + countAllies(RobotType.GARDENER));


        } else {
            allyCounts[0] = Counter.get(400) + Counter.get(406);
            allyCounts[1] = Counter.get(401) + Counter.get(407);
            allyCounts[2] = Counter.get(402) + Counter.get(408);
            allyCounts[3] = Counter.get(403) + Counter.get(409);
            allyCounts[4] = Counter.get(404) + Counter.get(410);
            allyCounts[5] = Counter.get(405) + Counter.get(411);

            activeGardenersCount = Counter.get(426);
            scoutHuntersCount = Counter.get(322);

            enemyCounts[0] = rc.readBroadcast(414);
            enemyCounts[1] = rc.readBroadcast(415);
            enemyCounts[2] = rc.readBroadcast(416);
            enemyCounts[3] = rc.readBroadcast(417);
            enemyCounts[4] = rc.readBroadcast(418);
            enemyCounts[5] = rc.readBroadcast(419);

            reportBloom[0] = rc.readBroadcast(420);
            reportBloom[1] = rc.readBroadcast(421);
            reportBloom[2] = rc.readBroadcast(422);
            reportBloom[3] = rc.readBroadcast(423);
            reportBloom[4] = rc.readBroadcast(424);
            reportBloom[5] = rc.readBroadcast(425);
        }
        Counter.increment(400 + myType);

        // Only gardeners have buildees
        if (myType == 1) {
            if (frame <= (buildees[0] & 0xFFFF)) {
                int robotType = buildees[0] >>> 16;
                Counter.increment(406 + robotType);
            }

            if (frame <= (buildees[1] & 0xFFFF)) {
                int robotType = buildees[1] >>> 16;
                Counter.increment(406 + robotType);
            }
        }
        // if (Util.DEBUG) System.out.println("after: " + Clock.getBytecodeNum());
    }

    public static void reportBuild(RobotType robotType) throws GameActionException {
        int index = typeToInt(robotType);
        Counter.increment(406 + index);

        // gardeners activate immediately
        if (robotType != RobotType.GARDENER) {
            buildees[frame < (buildees[0] & 0xFFFF) ? 1 : 0] = (frame + 20) + (index << 16);
        }else{
            reportActiveGardener();
        }
    }

    public static void reportEnemies(RobotInfo[] ris) throws GameActionException {
        //if (Util.DEBUG) System.out.println("before reporting enemies: " + Clock.getBytecodeNum() + "frame: " + rc.getRoundNum());
        int length = ris.length;
        int numReports = rc.readBroadcast(201);
        if (numReports == 98) {
            return;
        }
        int ID, h1, n1, b1, h2, n2, b2, h3, n3, b3, info;
        for (int i = 0; i < length; ++i) {
            RobotInfo ri = ris[i];
            if (ri.team.equals(myTeam)) {
                continue;
            }
            if (Util.DEBUG) System.out.println("Reporting enemy at " + ri.location);
            ID = ri.ID;

            h1 = ID % 192;
            n1 = h1 / 32;
            b1 = 1 << (h1 % 32);


            if ((reportBloom[n1] & b1) != 0) {
                continue;
            }

            tempBloom[n1] |= b1;

            info = ((int) Math.round(ri.location.x * 16) << 18) | ((int) Math.round(ri.location.y * 16) << 4) | typeToInt(ri.type);
            rc.broadcast(numReports + 202, info);
            rc.broadcast(numReports + 203, ID);
            //if (Util.DEBUG) System.out.println("Reported unit ID: " + ID + " type: " + typeToInt(ri.type) + " to cell " + (numReports + 202) + "report: " + info);
            numReports += 2;
            if (numReports == 98) {
                break;
            }
        }
        rc.broadcast(201, numReports);
        if (tempBloom[0] != 0) {
            rc.broadcast(420, reportBloom[0] | tempBloom[0]);
            tempBloom[0] = 0;
        }
        if (tempBloom[1] != 0) {
            rc.broadcast(421, reportBloom[1] | tempBloom[1]);
            tempBloom[1] = 0;
        }
        if (tempBloom[2] != 0) {
            rc.broadcast(422, reportBloom[2] | tempBloom[2]);
            tempBloom[2] = 0;
        }
        if (tempBloom[3] != 0) {
            rc.broadcast(423, reportBloom[3] | tempBloom[3]);
            tempBloom[3] = 0;
        }
        if (tempBloom[4] != 0) {
            rc.broadcast(424, reportBloom[4] | tempBloom[4]);
            tempBloom[4] = 0;
        }
        if (tempBloom[5] != 0) {
            rc.broadcast(425, reportBloom[5] | tempBloom[5]);
            tempBloom[5] = 0;
        }
        //if (Util.DEBUG) System.out.println("after reporting enemies: " + Clock.getBytecodeNum() + "frame: " + rc.getRoundNum());
    }


    public static void reportEnemy(MapLocation location, RobotType type, int ID) throws GameActionException {
        int h1 = ID % 192;
        int n1 = h1 / 32;
        int b1 = 1 << (h1 % 32);


        if ((reportBloom[n1] & b1) != 0) {
            return;
        }

        int numReports = rc.readBroadcast(201);
        if (numReports == 98) {
            return;
        }
        int info = ((int) Math.round(location.x * 16) << 18) | ((int) Math.round(location.y * 16) << 4) | typeToInt(type);
        rc.broadcast(numReports + 202, info);
        rc.broadcast(numReports + 203, ID);
        rc.broadcast(201, numReports + 2);


    }


    public static void deleteEnemyReport(MapLocation location) throws GameActionException {
        if (Util.DEBUG) System.out.println("Clearing invalid report at " + location);
        if (Util.DEBUG) rc.setIndicatorDot(location, 255, 0, 0);
        int info = ((int) Math.round(location.x * 16) << 14) | ((int) Math.round(location.y * 16));
        int numDeletes = rc.readBroadcast(427);
        if (numDeletes == 22) {
            return;
        }
        rc.broadcast(427, numDeletes + 1);
        rc.broadcast(428 + numDeletes, info);
    }

    public static float getUnitX(int info) {
        return ((info & 0b11111111111111000000000000000000) >>> 18) / 16.0f;
    }


    public static float getUnitY(int info) {
        return ((info & 0b00000000000000111111111111110000) >>> 4) / 16.0f;
    }

    public static float getTreeX(int info) {
        return (info & 0b11111111110000000000000000000000) >>> 22;
    }

    public static float getTreeY(int info) {
        return (info & 0b00000000001111111111000000000000) >>> 12;
    }

    public static RobotType getUnitType(int info) {
        return intToType(info & 0b00000000000000000000000000000111);
    }


    public static boolean getLandContact() throws GameActionException {
        return rc.readBroadcast(0) != 0;
    }

    public static void reportContact() throws GameActionException {
        rc.broadcast(0, 1);
    }

    // returns the index, so you can mark it as cut later
    public static boolean requestTreeCut(TreeInfo ti) throws GameActionException {
        int index = ti.containedRobot != null ? 302 : 305;
        int zero_index = -1;
        int data;
        for (; index <= 320; ++index) {
            data = rc.readBroadcast(index);
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
        rc.broadcast(zero_index, info);
        rc.broadcast(301, rc.readBroadcast(301) + 1);
        return true;
    }

    public static MapLocation findTreeToCut() throws GameActionException {
        int data;
        for (int index = 302; index <= 320; ++index) {
            data = rc.readBroadcast(index);
            if (data != 0) {
                return new MapLocation(getTreeX(data), getTreeY(data));
            }
        }
        return null;
    }

    public static MapLocation findClosestTreeToCut(MapLocation myLocation) throws GameActionException {
        MapLocation tree, closest = null;
        int data;
        for (int index = 302; index <= 320; ++index) {
            data = rc.readBroadcast(index);
            if (data != 0) {
                tree = new MapLocation(getTreeX(data), getTreeY(data));
                if (closest == null || closest.distanceTo(myLocation) > tree.distanceTo(myLocation)) {
                    closest = tree;
                }
            }
        }
        return closest;
    }

    public static void cleanupClosestTreeToCut(MapLocation myLocation) throws GameActionException {
        MapLocation tree = null;
        int data;
        int time = Clock.getBytecodeNum();
        for (int index = 302; index <= 320; ++index) {
            data = rc.readBroadcast(index);
            if (data != 0) {
                tree = new MapLocation(getTreeX(data), getTreeY(data));
                if (rc.canSenseLocation(tree) && rc.senseTreeAtLocation(tree) == null) {
                    if (Util.DEBUG) System.out.println("Cleaning up tree at " + tree);
                    reportTreeCut(tree);
                }
            }
        }
        time = Clock.getBytecodeNum() - time;
        if (time > 300) {
            if (Util.DEBUG) System.out.println("cleaning up trees took " + time);
        }
    }

    public static void reportTreeCut(MapLocation location) throws GameActionException {
        int loc = ((int) location.x << 22) | ((int) location.y << 12);
        int data;
        for (int index = 302; index <= 320; ++index) {
            data = rc.readBroadcast(index);
            if (((data ^ loc) & 0b11111111111111111111000000000000) == 0) {
                rc.broadcast(index, 0);
                rc.broadcast(301, rc.readBroadcast(301) - 1);
                break;
            }
        }
    }

    public static int countTreeCutRequests() throws GameActionException {
        return rc.readBroadcast(301);
    }


    public static void reportActiveGardener() throws GameActionException {
        Counter.increment(426);
    }

    public static int countActiveGardeners() throws GameActionException {
        return activeGardenersCount;
    }

    public static void reportScoutHunter() throws GameActionException {
        Counter.increment(322);
    }

    public static int countScoutHunters() throws GameActionException {
        return scoutHuntersCount;
    }

    public static void setAlarm() throws GameActionException {
        rc.broadcast(321, rc.getRoundNum());
    }

    public static boolean getAlarm() throws GameActionException {
        int lastAlarm = rc.readBroadcast(321);
        if (Util.DEBUG) System.out.println("Last alarm was " + lastAlarm);
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
