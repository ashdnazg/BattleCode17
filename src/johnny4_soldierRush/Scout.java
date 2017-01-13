package johnny4_soldierRush;

import battlecode.common.*;
import johnny4_soldierRush.Map;

import static johnny4_soldierRush.Util.*;

public class Scout {

    RobotController rc;
    johnny4_soldierRush.Map map;
    johnny4_soldierRush.Radio radio;
    final boolean isShaker;
    final boolean isRoamer;

    public Scout(RobotController rc) {
        this.rc = rc;
        this.radio = new johnny4_soldierRush.Radio(rc);
        this.map = new Map(rc, radio);
        isShaker = rc.getID() % 5 == 0;
        isRoamer = rc.getID() % 2 == 0;
        for (int i = 0; i < visitedBroadcasts.length; i++){
            visitedBroadcasts[i] = new MapLocation(0,0);
        }
    }

    public void run() {
        while (true) {
            tick();
            Clock.yield();
        }
    }

    float fx, fy, dx, dy, mag;
    MapLocation[] otherScouts = new MapLocation[100];
    Direction lastDirection = randomDirection();
    TreeInfo toShake = null;
    MapLocation lastCivilian = null;
    BulletInfo[] bullets;
    MapLocation[] visitedBroadcasts = new MapLocation[10];
    int rollingBroadcastIndex = 0;
    MapLocation nextLumberjack;
    final float MIN_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f + RobotType.LUMBERJACK.strideRadius;

    private boolean canMove(Direction dir){
        MapLocation nloc = rc.getLocation().add(dir, RobotType.SCOUT.strideRadius);
        if (nextLumberjack != null && nloc.distanceTo(nextLumberjack) < MIN_LUMBERJACK_DIST) return false;
        float br = RobotType.SCOUT.bodyRadius;
        for (BulletInfo bi : bullets){
            if (bi.location.distanceTo(nloc) < br){
                return false;
            }
        }
        return rc.canMove(dir);
    }
    private boolean canMove(Direction dir, float dist){
        try {
            MapLocation nloc = rc.getLocation().add(dir, dist);
            if (nextLumberjack != null && nloc.distanceTo(nextLumberjack) < MIN_LUMBERJACK_DIST) return false;
            float br = RobotType.SCOUT.bodyRadius;
            for (BulletInfo bi : bullets) {
                if (bi.location.distanceTo(nloc) < br) {
                    return false;
                }
            }
            return rc.canMove(dir, dist);
        }catch(Exception ex){

            System.out.println("canMove exception with args " + dir + ": " + dist);
            ex.printStackTrace();
            return false;
        }
    }


    float circleDir = 0f;

    protected void tick() {
        try {
            if (rc.getTeamBullets() >= 10000f) {
                rc.donate(10000f);
            }

            int frame = rc.getRoundNum();
            radio.frame = frame;
            MapLocation myLocation = rc.getLocation();

            MapLocation nextEnemy = null;
            MapLocation nextCivilian = null;
            float civSize = 0;
            float civMinDist = 10000f;
            boolean longRangeCiv = false;
            boolean longRangeEnemy = false;
            int nearbyAllies = 0;

            RobotInfo nearbyRobots[] = map.sense();
            TreeInfo trees[] = rc.senseNearbyTrees();
            bullets = rc.senseNearbyBullets();
            nextLumberjack = null;
            for (RobotInfo r : nearbyRobots) {

                RobotType ut = r.getType();
                if (!r.getTeam().equals(rc.getTeam())) {
                    if ((ut == RobotType.GARDENER) && (civMinDist > r.location.distanceTo(myLocation) || lastCivilian != null && r.location.distanceTo(lastCivilian) < 3)) {
                        nextCivilian = r.location;
                        civMinDist = (lastCivilian != null && r.location.distanceTo(lastCivilian) < 3) ? 0f : (r.location.distanceTo(myLocation));
                        civSize = ut.bodyRadius;
                    }
                    if ((ut == RobotType.LUMBERJACK || ut == RobotType.SOLDIER || ut == RobotType.TANK) && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > r.location.distanceTo(myLocation)) && r.moveCount + r.attackCount > 0) {
                        nextEnemy = r.location;
                    }
                } else {
                    nearbyAllies++;
                }
                if (ut == RobotType.LUMBERJACK && !r.getTeam().equals(rc.getTeam()) && r.moveCount + r.attackCount > 0){
                    if (nextLumberjack == null || nextLumberjack.distanceTo(myLocation) > r.location.distanceTo(myLocation)){
                        nextLumberjack = r.location;
                    }
                }
            }
            if (nextCivilian == null) {
                longRangeCiv = true;
                if (!isRoamer) {
                    if (lastCivilian != null && lastCivilian.distanceTo(myLocation) > 0.8f * RobotType.SCOUT.sensorRadius) {
                        nextCivilian = lastCivilian;
                    } else {
                        nextCivilian = map.getTarget(myLocation, 2, 10, 0.8f * RobotType.SCOUT.sensorRadius);
                        lastCivilian = null;
                    }
                    if (nextCivilian == null) {
                        nextCivilian = map.getTarget(myLocation, 2, 30, 3.5f * RobotType.SCOUT.sensorRadius);
                        if (nextCivilian == null) {
                            System.out.println("no target");
                        }
                    }
                }else{
                    nextCivilian = map.getTarget(myLocation, 2, 20, 0.8f * RobotType.SCOUT.sensorRadius);
                    if (nextCivilian == null) {
                        if (lastCivilian != null && lastCivilian.distanceTo(myLocation) > 0.8f * RobotType.SCOUT.sensorRadius) {
                            nextCivilian = lastCivilian;
                        } else {
                            lastCivilian = null;
                            MapLocation[] broadcasts = rc.senseBroadcastingRobotLocations();
                            if (broadcasts.length > 0) {
                                for (MapLocation bc : broadcasts){
                                    boolean invalid = false;
                                    for (MapLocation known : visitedBroadcasts){
                                        if (known.distanceTo(bc) < 6){
                                            invalid = true;
                                        }
                                    }
                                    if (invalid) continue;
                                    if (nextCivilian == null || nextCivilian.distanceTo(myLocation) < bc.distanceTo(myLocation)){
                                        nextCivilian = bc;
                                    }
                                }
                                if (nextCivilian != null){
                                    visitedBroadcasts[rollingBroadcastIndex ++] = nextCivilian;
                                    rollingBroadcastIndex %= visitedBroadcasts.length;
                                }
                                System.out.println("Going to broadcaster at " + nextCivilian);
                            }
                        }
                    }
                }
            }
            lastCivilian = nextCivilian;

            if (frame % 8 == 0) {
                radio.reportMyPosition(myLocation);
                otherScouts = radio.getAllyPositions();
                //System.out.println(Clock.getBytecodesLeft() + " for scout");
                fx = fy = 0;
                //System.out.println("Im at " + myLocation);
                for (int i = 0; i < otherScouts.length; i++) {
                    if (otherScouts[i] == null) {
                        //System.out.println(i + " other scouts");
                        break;
                    }
                    //System.out.println("Ohter scout at " + otherScouts[i]);
                    float dist = myLocation.distanceTo(otherScouts[i]);
                    if (dist > 2 * RobotType.SCOUT.sensorRadius) continue;
                    dx = (myLocation.x - otherScouts[i].x);
                    dy = (myLocation.y - otherScouts[i].y);
                    mag = (float) Math.sqrt(dx * dx + dy * dy);
                    fx += dx / mag / dist;
                    fy += dy / mag / dist;

                    //System.out.println("Moving " + weight * dx / mag + " | " + weight * dy / mag);
                }
            }


            mag = (float) Math.sqrt(fx * fx + fy * fy);
            if (nextEnemy == null && nextCivilian == null) {
                longRangeEnemy = true;
                nextEnemy = map.getTarget(myLocation, 0, 9, 0.8f * RobotType.SCOUT.sensorRadius);
                if (nextEnemy == null) {
                    nextEnemy = map.getTarget(myLocation, 3, 80);
                }
            }
            float dist = 100000f;
            boolean hasMoved = tryEvade(bullets);
            myLocation = rc.getLocation();
            if (hasMoved && Clock.getBytecodesLeft() < 2000) {
                System.out.println("Aborting scout early on " + frame);
                return;
            }
            if (nextEnemy != null) {
                dist = myLocation.distanceTo(nextEnemy);
            }
            float lumberDist = 10000f;
            if (nextLumberjack != null){
                lumberDist = nextLumberjack.distanceTo(myLocation);
                //System.out.println("Lumberjack at " + nextLumberjack);
            }
            if (toShake != null && dist > 5) {
                //System.out.println("Shaking " + toShake.getLocation());
                if (!hasMoved && !LJ_tryMove(myLocation.directionTo(toShake.getLocation()))) {
                    if (canMove(myLocation.directionTo(toShake.getLocation()), 0.5f)) {
                        rc.move(myLocation.directionTo(toShake.getLocation()), 0.5f);
                        hasMoved = true;
                        myLocation = rc.getLocation();
                    } else {
                        toShake = null;
                    }
                }
                if (rc.canShake(toShake.getID())) {
                    rc.shake(toShake.getID());
                    System.out.println("Shaken " + toShake.getLocation());
                    toShake = null;
                }
            } else {
                toShake = null;
                if (nearbyAllies > 5 + rc.getID() % 5) {
                    //System.out.println("Too many allies.");
                }
                if (nextCivilian != null && dist > 3.5 && nearbyAllies < 5 + rc.getID() % 5 && lumberDist > MIN_LUMBERJACK_DIST) {
                    //System.out.println("attacking " + nextCivilian + " : " + longRangeCiv);
                    if (nextCivilian.distanceTo(myLocation) - civSize > 6.1) {
                        if (!hasMoved && !LJ_tryMove(myLocation.directionTo(nextCivilian))) {
                            if (canMove(myLocation.directionTo(nextCivilian), 0.5f)) {
                                rc.move(myLocation.directionTo(nextCivilian), 0.5f);
                                hasMoved = true;
                                myLocation = rc.getLocation();
                            } else {
                            }
                        } else {
                            hasMoved = true;
                            myLocation = rc.getLocation();
                        }
                    }
                    if (nextCivilian.distanceTo(myLocation) - civSize < 6.1) {
                    /*if (rc.canFirePentadShot()) {
                        rc.firePentadShot(myLocation.directionTo(nextCivilian));
                    }else if (rc.canFireTriadShot()) {
                        rc.fireTriadShot(myLocation.directionTo(nextCivilian));
                    } else*/
                        boolean hasFired = longRangeCiv;
                        if (!hasFired && checkLineOfFire(myLocation, nextCivilian, trees, nearbyRobots, RobotType.SCOUT.bodyRadius)) {
                            if (rc.canFireSingleShot()) {
                                rc.fireSingleShot(myLocation.directionTo(nextCivilian));
                                hasFired = true;
                            }
                        }
                        if (!hasMoved){
                            TreeInfo best = null;
                            float mindist = 100000;
                            for (TreeInfo ti : trees){
                                if (ti.location.distanceTo(nextCivilian) - ti.radius - ((ti.getID() % 17) / 170f) < mindist){
                                    mindist = ti.location.distanceTo(nextCivilian) - ti.radius - ((ti.getID() % 17) / 170f);
                                    best = ti;
                                }
                            }
                            if (best != null) {
                                MapLocation pos = best.location.add(best.location.directionTo(nextCivilian), best.radius - RobotType.SCOUT.bodyRadius);
                                if (myLocation.equals(pos)){
                                    hasMoved = true;
                                }else
                                if (canMove(myLocation.directionTo(pos), Math.min(RobotType.SCOUT.strideRadius, myLocation.distanceTo(pos)))){
                                    rc.move(myLocation.directionTo(pos), Math.min(RobotType.SCOUT.strideRadius, myLocation.distanceTo(pos)));
                                    hasMoved = true;
                                }
                            }
                        }
                        Direction dir;
                        int tries = 0;
                        while (!hasMoved && tries++ < 30) {
                            if (circleDir > 0.5) {
                                dir = myLocation.directionTo(nextCivilian).rotateRightDegrees(2 * tries + 42);
                            } else {
                                dir = myLocation.directionTo(nextCivilian).rotateLeftDegrees(2 * tries + 42);
                            }
                            if (!hasMoved && canMove(dir, 2f)) {
                                try {
                                    rc.move(dir, 2f);
                                } catch (Exception ex) {
                                }
                                hasMoved = true;
                                myLocation = rc.getLocation();
                            } else {
                                circleDir = (float) Math.random();
                            }
                        }
                        if (!hasFired && checkLineOfFire(myLocation, nextCivilian, trees, nearbyRobots, RobotType.SCOUT.bodyRadius)) {
                            if (rc.canFireSingleShot()) {
                                rc.fireSingleShot(myLocation.directionTo(nextCivilian));
                            }
                        }
                    }
                }else if (dist > 3.5) {
                    if (!hasMoved) {
                        int tries = 0;
                        while (!canMove(lastDirection) && tries ++ < 10) {
                            if (circleDir > 0.5f) {
                                lastDirection = lastDirection.rotateRightDegrees((float) Math.random() * 42 + 30);
                            }else{
                                lastDirection = lastDirection.rotateLeftDegrees((float) Math.random() * 42 + 30);
                            }
                        }
                        if (tries > 1 && Math.random() < 0.12){
                            circleDir = (float) Math.random();
                        }
                        if (tries < 10) {
                            rc.move(lastDirection);
                            myLocation = rc.getLocation();
                        }
                    }
                } else if (nextEnemy != null && (Math.random() > 0.4 || dist < RobotType.SOLDIER.sensorRadius || mag < 1e-20f) && nearbyAllies < 5 + rc.getID() % 5) {
                    if (dist < RobotType.SOLDIER.sensorRadius) {
                        if (!longRangeEnemy && checkLineOfFire(myLocation, nextEnemy, trees, nearbyRobots, RobotType.SCOUT.bodyRadius)) {
                            if (rc.getTeamBullets() > 150 && rc.canFireSingleShot()) {
                                rc.fireSingleShot(myLocation.directionTo(nextEnemy));
                            }
                        } else {
                        }
                        if (!hasMoved) LJ_tryMove(nextEnemy.directionTo(myLocation));
                        myLocation = rc.getLocation();
                    } else {
                        //System.out.println("Moving towards enemy at distance " + dist);
                        if (!hasMoved) LJ_tryMove(myLocation.directionTo(nextEnemy), 70, 1);
                        myLocation = rc.getLocation();
                    }
                }  else {
                    if (!hasMoved)
                        LJ_tryMove(new Direction(RobotType.SCOUT.strideRadius * fx / mag, RobotType.SCOUT.strideRadius * fy / mag));
                    myLocation = rc.getLocation();
                }
                if (Clock.getBytecodesLeft() < 1000) return;
                for (TreeInfo t : trees) {
                    if (rc.canShake(t.location)){
                        rc.shake(t.location);
                    }
                    if (t.containedRobot != null){
                        radio.requestTreeCut(t);
                        System.out.println("Requesting christmas tree to be cut!");
                    }
                }
                if (toShake == null && isShaker) {
                    for (TreeInfo t : trees) {
                        if (t.getContainedBullets() > 10) {
                            toShake = t;
                            break;
                        }
                    }
                }
                if (rc.getRoundNum() - frame > 0 && frame % 8 != 0 && (longRangeCiv == false && longRangeEnemy == false)) {
                    System.out.println("Scout took " + (rc.getRoundNum() - frame) + " frames at " + frame + " : " + longRangeCiv + " " + longRangeEnemy);
                }
            }

        } catch (Exception e) {
            System.out.println("Scout Exception");
            e.printStackTrace();
        }
    }

    boolean LJ_tryMove(Direction dir) {
        try {
            return LJ_tryMove(dir, 17 + (float)Math.random() * 17, 6);
        } catch (Exception ex) {
            return false;
        }
    }

    boolean LJ_tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        if (canMove(dir)) {
            rc.move(dir);
            return true;
        }
        boolean moved = false;
        int currentCheck = 1;

        while (currentCheck <= checksPerSide) {
            try {
                Direction d = dir.rotateLeftDegrees(degreeOffset * currentCheck);
                if (canMove(d)) {
                    rc.move(d);
                    return true;
                }
                d = dir.rotateRightDegrees(degreeOffset * currentCheck);
                if (canMove(d)) {
                    rc.move(d);
                    return true;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            currentCheck++;
        }
        return false;
    }
}
