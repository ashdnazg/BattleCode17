package johnny4_fc0ac89;

import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.TreeInfo;

public abstract class Grid {

    RobotController rc;

    public Grid(RobotController rc) {
        this.rc = rc;
    }

    public abstract MapLocation getNearestWalkableLocation(MapLocation myLocation);

    public abstract MapLocation getNearestPlantableLocation(MapLocation myLocation, TreeInfo[] treeInfos);

}
