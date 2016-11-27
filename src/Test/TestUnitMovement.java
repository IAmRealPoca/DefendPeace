package Test;

import CommandingOfficers.CmdrStrong;
import CommandingOfficers.Commander;
import Engine.GameAction;
import Terrain.GameMap;
import Units.Unit;
import Units.UnitModel.UnitEnum;

public class TestUnitMovement extends TestCase
{
  private static Commander testCo1;
  private static Commander testCo2;
  private static GameMap testMap;

  /** Make two COs and a GameMap to use with this test case. */
  private void setupTest()
  {
    testCo1 = new CmdrStrong();
    testCo2 = new Commander();
    Commander[] cos = { testCo1, testCo2 };

    // TODO: This will have to change once GameMap doesn't build a default map.
    testMap = new GameMap(cos);

    // Remove the default units. TODO: Remove this once there isn't a default map.
    testMap.getLocation(6, 5).setResident(null);
    testMap.getLocation(8, 4).setResident(null);
  }

  @Override
  public boolean runTest()
  {
    // Create a CO and a GameMap.
    setupTest();

    boolean testPassed = true;
    testPassed &= validate(testSimpleMovement(), "  Simple movement test failed.");
    // This test is currently not supported, but is slated for future consideration.
    //testPassed &= validate(testOutOfRangeMovement(), "  Move out of range test failed.");
    return testPassed;
  }

  /** Make an infantry unit, and order him to move. Easy peasy. */
  private boolean testSimpleMovement()
  {
    // Add a Unit and try to move it.
    Unit mover = addUnit(testMap, testCo1, UnitEnum.INFANTRY, 4, 4);
    GameAction ga = new GameAction(mover, 6, 5, GameAction.ActionType.WAIT);
    ga.execute(testMap);

    // Evaluate the test.    
    boolean testPassed = validate(testMap.getLocation(4, 4).getResident() == null, "    Infantry is still at the start point.");
    testPassed &= validate(testMap.getLocation(6, 5).getResident() == mover, "    Infantry is not at the destination.");
    testPassed &= validate((6 == mover.x) && (5 == mover.y), "    Infantry doesn't think he's at the destination.");

    // Clean up for the next test.
    testMap.removeUnit(mover);

    return testPassed;
  }

  /** Make an Infantry unit, and tell him to move farther than he can. */
  private boolean testOutOfRangeMovement()
  {
    // Make a unit and add it to the map.
    Unit mover = addUnit(testMap, testCo1, UnitEnum.INFANTRY, 4, 4);

    // Make an action to move the unit 5 spaces away, and execute it.
    GameAction ga = new GameAction(mover, 7, 6, GameAction.ActionType.WAIT);
    ga.execute(testMap);

    // Make sure the action didn't actually execute.
    boolean testPassed = validate(testMap.getLocation(4, 4).getResident() == mover, "    Infantry moved when he shouldn't have.");
    testPassed &= validate(4 == mover.x && 4 == mover.y, "    Infantry thinks he moved when he should not have.");

    // Clean up for the next test.
    testMap.removeUnit(mover);

    return testPassed;
  }
}
