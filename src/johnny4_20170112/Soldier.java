package johnny4_20170112;

import battlecode.common.*;

import java.awt.*;
import java.util.Optional;

import static johnny4_20170112.Util.*;

public class Soldier {

    RobotController rc;
    Map map;
    Radio radio;

    public Soldier(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        stuckLocation = rc.getLocation();
        stuckSince = rc.getRoundNum();
    }

    public void run() {
        while (true) {
            tick();
            Clock.yield();
        }
    }

    private boolean canMove(Direction dir){
        MapLocation nloc = rc.getLocation().add(dir, RobotType.SCOUT.strideRadius);
        if (nextLumberjack != null && nloc.distanceTo(nextLumberjack) < MIN_LUMBERJACK_DIST) return false;
        float br = rc.getType().bodyRadius;
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
            float br = rc.getType().bodyRadius;
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
    MapLocation stuckLocation;
    int stuckSince;
    BulletInfo bullets[];
    MapLocation nextLumberjack;
    boolean evasionMode = true;
    final float MIN_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
    final float MIN_HOSTILE_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f + RobotType.LUMBERJACK.strideRadius;
    final float MIN_EVASION_DIST = 6f;

    protected void tick() {
        try {
            if (rc.getTeamBullets() >= 10000f) {
                rc.donate(10000f);
            }

            int frame = rc.getRoundNum();
            radio.frame = frame;
            MapLocation myLocation = rc.getLocation();
            bullets = rc.senseNearbyBullets();
            RobotInfo nearbyRobots[] = null;
            RobotType enemyType = RobotType.SOLDIER;
            if (frame % 8 == 0) {
                radio.reportMyPosition(myLocation);
                nearbyRobots = map.sense();
            }
            if (nearbyRobots == null) {
                nearbyRobots = map.sense(); //rc.senseNearbyRobots();
            }

            MapLocation nextEnemy = null;
            nextLumberjack = null;
            TreeInfo trees[] = rc.senseNearbyTrees();
            for (RobotInfo r : nearbyRobots) {
                if (!r.getTeam().equals(rc.getTeam()) && (nextEnemy == null || nextEnemy.distanceTo(myLocation) * enemyType.strideRadius > r.location.distanceTo(myLocation) * r.type.strideRadius)) {
                    nextEnemy = r.location;
                    enemyType = r.type;
                }
                if (r.getTeam().equals(rc.getTeam()) && r.type == RobotType.LUMBERJACK && (nextLumberjack == null || nextLumberjack.distanceTo(myLocation) > r.location.distanceTo(myLocation))) {
                    nextLumberjack = r.location;
                }
            }

            if (myLocation.distanceTo(stuckLocation) > 5){
                stuckSince = frame;
                stuckLocation = myLocation;
            }
            if (rc.getRoundNum() - stuckSince > 50){
                System.out.println("Stuck soldier reporting trees");
                stuckSince = 100000;
                for (TreeInfo t : trees){
                    if (t.getTeam().equals(rc.getTeam())) continue;
                    if (t.location.distanceTo(myLocation) < 5){
                        System.out.println("Reported tree at " + t.location);
                        radio.requestTreeCut(t);
                    }
                }
            }

            boolean longrange = false;
            if (nextEnemy == null) {
                longrange = true;
                nextEnemy = map.getTarget(myLocation, 0, 10);
                if (nextEnemy == null) {

                    nextEnemy = map.getTarget(myLocation, 0, 90);
                    //System.out.println("Soldier at " + myLocation + " attacking " + nextEnemy);
                }
            }
            boolean hasMoved = tryEvade(bullets);
            myLocation = rc.getLocation();
            float dist = 10000f;
            if (nextEnemy != null) {
                dist = myLocation.distanceTo(nextEnemy);

                boolean hasFired = longrange;
                if (!hasFired && checkLineOfFire(myLocation, nextEnemy, trees, nearbyRobots, RobotType.SOLDIER.bodyRadius) && dist < 8f / enemyType.strideRadius) {
                    hasFired = tryFire(nextEnemy, dist, enemyType.bodyRadius);
                }
                if (dist < 5 && nextLumberjack != null && nextLumberjack.distanceTo(myLocation) < MIN_LUMBERJACK_DIST){
                    if (tryMove(nextLumberjack.directionTo(myLocation))){
                        hasMoved = true;
                        myLocation = rc.getLocation();
                    }
                }
                if (rc.getTeamBullets() > 50) evasionMode = false;
                if (rc.getTeamBullets() < 10) evasionMode = true;
                if (evasionMode && (dist < MIN_EVASION_DIST || enemyType == RobotType.LUMBERJACK && dist < MIN_HOSTILE_LUMBERJACK_DIST)) {
                    if (!hasMoved && tryMove(nextEnemy.directionTo(myLocation))) {
                        hasMoved = true;
                        myLocation = rc.getLocation();
                    }
                } else {
                    if (!hasMoved) {
                        if ((longrange || dist > 8f / enemyType.strideRadius) && !evasionMode) {
                            if (tryMove(myLocation.directionTo(nextEnemy))){
                                hasMoved = true;
                                myLocation = rc.getLocation();
                            }
                        } else {
                            Direction dir;
                            int tries = 0;
                            while (!hasMoved && tries++ < 20) {
                                if (circleDir > 0.5) {
                                    dir = myLocation.directionTo(nextEnemy).rotateRightDegrees(4 * tries + 30);
                                } else {
                                    dir = myLocation.directionTo(nextEnemy).rotateLeftDegrees(4 * tries + 30);
                                }
                                if (!hasMoved && canMove(dir, 2f)) {
                                    rc.move(dir, 2f);
                                    hasMoved = true;
                                    myLocation = rc.getLocation();
                                } else {
                                    circleDir = (float) Math.random();
                                }
                            }
                        }

                    }
                }

                if (!hasFired && checkLineOfFire(myLocation, nextEnemy, trees, nearbyRobots, RobotType.SOLDIER.bodyRadius) && dist < 8f / enemyType.strideRadius) {
                    hasFired = tryFire(nextEnemy, dist, enemyType.bodyRadius);
                }
            } else if (!hasMoved) {
                tryMove(randomDirection());
                myLocation = rc.getLocation();
            }
            if (rc.getRoundNum() - frame > 0) {
                System.out.println("Soldier took " + (rc.getRoundNum() - frame) + " frames at " + frame + " using longrange " + longrange);
            }


        } catch (Exception e) {
            System.out.println("Soldier Exception");
            e.printStackTrace();
        }
    }

    boolean tryFire(MapLocation nextEnemy, float dist, float radius) throws GameActionException{
        MapLocation myLocation = rc.getLocation();
        if (dist - radius < 1.71 && rc.canFirePentadShot()) {
            System.out.println("Firing pentad");
            rc.firePentadShot(myLocation.directionTo(nextEnemy));
            return true;
        }else
        if (dist - radius < 2.31 && rc.canFireTriadShot()) {
            System.out.println("Firing triad");
            rc.fireTriadShot(myLocation.directionTo(nextEnemy));
            return true;
        }else
        if (rc.canFireSingleShot()) {
            rc.fireSingleShot(myLocation.directionTo(nextEnemy));
            return true;
        }
        return false;
    }
}
