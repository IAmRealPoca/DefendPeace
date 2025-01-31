package Test;

import CommandingOfficers.Commander;
import CommandingOfficers.Patch;
import Engine.Army;
import Engine.GameAction;
import Engine.GameInstance;
import Engine.GameScenario;
import Engine.GamePath;
import Engine.Utils;
import Engine.XYCoord;
import Engine.GameEvents.ArmyDefeatEvent;
import Engine.GameEvents.CreateUnitEvent;
import Engine.GameEvents.GameEvent;
import Engine.GameEvents.GameEventQueue;
import Engine.GameEvents.MoveEvent;
import Engine.GameEvents.UnitDieEvent;
import Engine.UnitActionLifecycles.BattleLifecycle;
import Engine.UnitActionLifecycles.CaptureLifecycle;
import Engine.UnitActionLifecycles.JoinLifecycle;
import Engine.UnitActionLifecycles.LoadLifecycle;
import Engine.UnitActionLifecycles.ResupplyLifecycle;
import Engine.UnitActionLifecycles.UnloadLifecycle;
import Engine.UnitActionLifecycles.WaitLifecycle;
import Terrain.MapLibrary;
import Terrain.MapMaster;
import Terrain.TerrainType;
import Units.Unit;
import Units.UnitModel;

public class TestGameEvent extends TestCase
{
  private static Commander testCo1;
  private static Commander testCo2;
  private static MapMaster testMap;
  private static GameInstance testGame;

  /** Make two COs and a MapMaster to use with this test case. */
  private void setupTest()
  {
    GameScenario scn = new GameScenario();
    testCo1 = new Patch(scn.rules);
    testCo2 = new Patch(scn.rules);
    Army[] cos = { new Army(scn, testCo1), new Army(scn, testCo2) };

    testMap = new MapMaster(cos, MapLibrary.getByName("Firing Range"));
    testGame = new GameInstance(cos, testMap);
  }

  @Override
  public boolean runTest()
  {
    setupTest();
    boolean testPassed = true;
    testPassed &= validate(testBattleEvent(), "  BattleEvent test failed.");
    testPassed &= validate(testCaptureEvent(), "  CaptureEvent test failed.");
    testPassed &= validate(testCreateUnitEvent(), "  CreateUnitEvent test failed.");
    testPassed &= validate(testLoadUnloadEvent(), "  LoadUnloadEvent test failed.");
    testPassed &= validate(testMoveEvent(), "  MoveEvent test failed.");
    testPassed &= validate(testUnitDieEvent(), "  UnitDieEvent test failed.");
    testPassed &= validate(testResupplyEvent(), "  Resupply test failed.");
    testPassed &= validate(testUnitJoinEvent(), "  Join test failed.");
    testPassed &= validate(testCommanderDefeatEvent(), "  ArmyDefeatEvent test failed."); // Put this one last because it alters the map.

    return testPassed;
  }

  private boolean testBattleEvent()
  {
    boolean testPassed = true;

    // Add our combatants
    Unit infA = addUnit(testMap, testCo1, UnitModel.TROOP, 1, 1);
    Unit infB = addUnit(testMap, testCo2, UnitModel.TROOP, 1, 2);

    BattleLifecycle.BattleEvent event = new BattleLifecycle.BattleEvent(infA, infB, Utils.findShortestPath(infA, new XYCoord(2, 2), testMap), testMap);
    event.performEvent(testMap);
    testPassed &= validate(infB.getHP() < 10, "    Defender Was not damaged");
    testPassed &= validate(infA.getHP() < 10, "    Defender did not counter-attack");

    // Clean up
    testMap.removeUnit(infA);
    testMap.removeUnit(infB);

    return testPassed;
  }

  private boolean testCaptureEvent()
  {
    boolean testPassed = true;

    // We loaded Firing Range, so we expect a city at location (2, 2)
    Terrain.MapLocation city = testMap.getLocation(2, 2);
    testPassed &= validate(city.getEnvironment().terrainType == TerrainType.CITY, "    No city at (2, 2).");
    testPassed &= validate(city.getOwner() == null, "    City should not be owned by any CO yet.");

    // Add a unit
    Unit infA = addUnit(testMap, testCo1, UnitModel.TROOP, 2, 2);
    testPassed &= validate(infA.getCaptureProgress() == 0, "    Infantry capture progress is not 0.");

    // Create a new event, and ensure it does not predict full capture in one turn.
    CaptureLifecycle.CaptureEvent captureEvent = new CaptureLifecycle.CaptureEvent(infA, city);
    testPassed &= validate(captureEvent.willCapture() == false, "    Event incorrectly predicts capture will succeed.");
    // NOTE: The prediction will be unreliable after performing the event. I'm re-using it here for convenience, but
    //       GameEvents are really designed to be single-use.

    captureEvent.performEvent(testMap);
    testPassed &= validate(infA.getCaptureProgress() == 10, "    Infantry capture progress is not 10.");

    // Hurt the unit so he won't capture as fast.
    infA.damageHP(5.0);
    captureEvent.performEvent(testMap);
    testPassed &= validate(infA.getCaptureProgress() == 15, "    Infantry capture progress is not 15.");

    // Move the unit; he should lose his capture progress.
    infA.initTurn(testMap);
    GameAction moveAction = new WaitLifecycle.WaitAction(infA, Utils.findShortestPath(infA, 1, 2, testMap));
    performGameAction(moveAction, testGame);
    infA.initTurn(testMap);
    GameAction moveAction2 = new WaitLifecycle.WaitAction(infA, Utils.findShortestPath(infA, 2, 2, testMap));
    performGameAction(moveAction2, testGame);

    // 5, 10, 15
    captureEvent.performEvent(testMap);
    testPassed &= validate(infA.getCaptureProgress() == 5,
        "    Infantry capture progress should be 5, not " + infA.getCaptureProgress() + ".");
    captureEvent.performEvent(testMap);
    testPassed &= validate(infA.getCaptureProgress() == 10,
        "    Infantry capture progress should be 10, not " + infA.getCaptureProgress() + ".");
    captureEvent.performEvent(testMap);
    testPassed &= validate(infA.getCaptureProgress() == 15,
        "    Infantry capture progress should be 15, not " + infA.getCaptureProgress() + ".");

    // Recreate the captureEvent so we can check the prediction again.
    captureEvent = new CaptureLifecycle.CaptureEvent(infA, city);
    testPassed &= validate(captureEvent.willCapture() == true, "    Event incorrectly predicts failure to capture.");
    captureEvent.performEvent(testMap);
    testPassed &= validate(infA.getCaptureProgress() == 0, "    Infantry capture progress should be 0 again.");
    testPassed &= validate(city.getOwner() == infA.CO, "    City is not owned by the infantry's CO, but should be.");

    // Clean up
    testMap.removeUnit(infA);

    return testPassed;
  }

  private boolean testCreateUnitEvent()
  {
    boolean testPassed = true;

    XYCoord coords = new XYCoord(13, 8);
    CreateUnitEvent event = new CreateUnitEvent(testCo1, testCo1.getUnitModel(UnitModel.TROOP), coords);

    testPassed &= validate(testMap.getLocation(coords).getResident() == null, "    MapLocation is already occupied.");

    event.performEvent(testMap);

    Unit resident = testMap.getLocation(coords).getResident();
    testPassed &= validate(resident != null, "    Failed to create a unit.");
    testPassed &= validate(resident.model.isAll(UnitModel.TROOP | UnitModel.LAND), "    Unit created with wrong type.");
    testPassed &= validate(resident.CO == testCo1, "    Unit created with wrong CO.");

    // Clean up.
    testMap.removeUnit(resident);

    return testPassed;
  }

  private boolean testLoadUnloadEvent()
  {
    boolean testPassed = true;

    // Add some units.
    Unit inf = addUnit(testMap, testCo1, UnitModel.TROOP, 2, 2);
    Unit mech = addUnit(testMap, testCo1, UnitModel.MECH, 2, 3);
    Unit apc = addUnit(testMap, testCo1, UnitModel.TRANSPORT, 3, 2);

    // Try to load the infantry onto the mech unit, and ensure it fails.
    new LoadLifecycle.LoadEvent(inf, mech).performEvent(testMap);
    testPassed &= validate(testMap.getLocation(2, 2).getResident() == inf, "    Infantry should still be at (2, 2).");
    testPassed &= validate(2 == inf.x && 2 == inf.y, "    Infantry should still think he is at (2, 2).");
    testPassed &= validate(mech.heldUnits.size() == 0, "    Mech should not have holding capacity.");

    // Try to load the infantry into the APC, and make sure it works.
    new LoadLifecycle.LoadEvent(inf, apc).performEvent(testMap);
    testPassed &= validate(testMap.getLocation(2, 2).getResident() == null, "   Infantry is still at his old map location.");
    testPassed &= validate(-1 == inf.x && -1 == inf.y, "    Infantry does not think he is in the transport.");
    testPassed &= validate(apc.heldUnits.size() == 1, "    APC is not holding 1 unit, but should be holding Infantry.");
    testPassed &= validate(apc.heldUnits.get(0).model.isAll(UnitModel.TROOP | UnitModel.LAND),
        "    Held unit is not infantry, but should be.");

    // Now see if we can also load the mech into the APC; verify this fails.
    new LoadLifecycle.LoadEvent(mech, apc).performEvent(testMap);
    testPassed &= validate(testMap.getLocation(2, 3).getResident() == mech, "    Mech should still be at (2, 3).");
    testPassed &= validate(2 == mech.x && 3 == mech.y, "    Mech does not think he is at (2, 3), but he should.");
    testPassed &= validate(apc.heldUnits.size() == 1, "    APC should still only be holding 1 unit.");

    // Unload the mech; this should fail, since he is not on the transport.
    new UnloadLifecycle.UnloadEvent(apc, mech, 3, 3).performEvent(testMap);
    testPassed &= validate(testMap.getLocation(3, 3).getResident() == null, "    MapLocation (3, 3) should have no residents.");
    testPassed &= validate(2 == mech.x && 3 == mech.y, "    Mech thinks he has moved, but should still be at (2, 3).");
    testPassed &= validate(apc.heldUnits.size() == 1, "    APC should still have one passenger.");

    // Unload the infantry; this should succeed.
    new UnloadLifecycle.UnloadEvent(apc, inf, 3, 3).performEvent(testMap);
    testPassed &= validate(testMap.getLocation(3, 3).getResident() == inf, "    Infantry is not at the dropoff point.");
    testPassed &= validate(apc.heldUnits.size() == 0,
        "    APC should have zero cargo, but heldUnits size is " + apc.heldUnits.size());
    testPassed &= validate(3 == inf.x && 3 == inf.y, "    Infantry does not think he is at dropoff point.");

    // Clean up
    testMap.removeUnit(inf);
    testMap.removeUnit(mech);
    testMap.removeUnit(apc);

    return testPassed;
  }

  boolean testMoveEvent()
  {
    boolean testPassed = true;

    // Add some units.
    Unit inf = addUnit(testMap, testCo1, UnitModel.TROOP, 2, 2);
    Unit mech = addUnit(testMap, testCo1, UnitModel.MECH, 2, 3);
    Unit apc = addUnit(testMap, testCo1, UnitModel.TRANSPORT, 3, 2);

    GamePath path = new GamePath();
    path.addWaypoint(3, 3); // we need two waypoints to not break compatibility with MoveEvent, since it assumes the first waypoint isn't used.
    path.addWaypoint(7, 5); // A suitable place to move (should be the middle of the road in Firing Range).

    // Move the infantry - Note that MoveEvent does not verify that this is a valid move for the unit. This is
    // expected to happen in GameAction, which is typically responsible for creating MoveEvents.
    new MoveEvent(inf, path).performEvent(testMap); // Move the infantry 8 spaces, woo!
    testPassed &= validate(7 == inf.x && 5 == inf.y, "    Infantry should think he is at (7, 5) after moving.");
    testPassed &= validate(testMap.getLocation(7, 5).getResident() == inf, "    Infantry is not at (7, 5) after moving.");

    path.addWaypoint(7, 6); // New endpoint.
    new MoveEvent(mech, path).performEvent(testMap);
    testPassed &= validate(7 == mech.x && 6 == mech.y, "    Mech should think he is at (7, 6) after moving.");
    testPassed &= validate(testMap.getLocation(7, 6).getResident() == mech, "    Mech is not at (7, 6) after moving.");

    path.addWaypoint(7, 0); // New endpoint over water.
    GameEventQueue events = new GameEventQueue();
    // Validation is now performed primarily in the enqueue function since there's too much complexity to handle it nicely in the constructor
    Utils.enqueueMoveEvent(testMap, mech, path, events);
    events.getFirst().performEvent(testMap); // This should not execute. Water is bad for grunts.
    testPassed &= validate(7 == mech.x && 6 == mech.y, "    Mech does not think he is at (7, 6), but should.");
    testPassed &= validate(testMap.getLocation(7, 6).getResident() == mech, "    Mech is not still at (7, 6), but should be.");
    testPassed &= validate(testMap.getLocation(7, 0).getResident() == null, "    MapLocation (7, 0) should still be empty.");

    path.addWaypoint(7, 5); // New endpoint to move apc over infantry.
    new MoveEvent(apc, path).performEvent(testMap); // This should not execute. Treads are bad for grunts.
    testPassed &= validate(7 == inf.x && 5 == inf.y, "    Infantry should still think he is at (7, 5).");
    testPassed &= validate(testMap.getLocation(7, 5).getResident() == inf, "    Infantry should still be at (7, 5).");
    testPassed &= validate(3 == apc.x && 2 == apc.y, "    APC should still think it is at (3, 2).");
    testPassed &= validate(testMap.getLocation(3, 2).getResident() == apc, "    APC should still be at (3, 2)");

    // Clean up.
    testMap.removeUnit(inf);
    testMap.removeUnit(mech);
    testMap.removeUnit(apc);

    return testPassed;
  }

  private boolean testUnitDieEvent()
  {
    boolean testPassed = true;

    // Add some units.
    Unit inf = addUnit(testMap, testCo1, UnitModel.TROOP, 2, 2);
    Unit mech = addUnit(testMap, testCo1, UnitModel.MECH, 2, 3);
    mech.damageHP(5); // Just for some variation.

    // Knock 'em dead.
    new UnitDieEvent(inf).performEvent(testMap);
    new UnitDieEvent(mech).performEvent(testMap);

    // Make sure the pins are down.
    testPassed &= validate(inf.getPreciseHP() == 0, "    Infantry still has health after dying.");
    testPassed &= validate(inf.x == -1 && inf.y == -1, "    Infantry still thinks he is on the map after death.");
    testPassed &= validate(testMap.getLocation(2, 2).getResident() == null, "    Infantry did not vacate his space after death.");
    testPassed &= validate(mech.getPreciseHP() == 0, "    Mech still has health after dying.");
    testPassed &= validate(mech.x == -1 && mech.y == -1, "    Mech still thinks he is on the map after death.");
    testPassed &= validate(testMap.getLocation(2, 3).getResident() == null, "    Mech did not vacate his space after death.");

    // If we got this far, the event itself works; Now verify that Utils.enqueueDeathEvent creates them correctly.
    // Create a lander holding an APC holding an infantry.
    Unit man = addUnit(testMap, testCo1, UnitModel.TROOP, 3, 1);
    Unit car = addUnit(testMap, testCo1, UnitModel.TRANSPORT | UnitModel.LAND, 2, 1);
    Unit boat = addUnit(testMap, testCo1, UnitModel.TRANSPORT | UnitModel.SEA, 1, 1);
    new LoadLifecycle.LoadEvent(man, car).performEvent(testMap);
    new LoadLifecycle.LoadEvent(car, boat).performEvent(testMap);

    // Sink the boat. All units it holds should also be accounted for.
    GameEventQueue geq = new GameEventQueue();
    Utils.enqueueDeathEvent(boat, geq);
    testPassed &= validate(geq.size() == 3, "    Utils.enqueueDeathEvent built the wrong number of events!");

    // No cleanup required.

    return testPassed;
  }

  boolean testResupplyEvent()
  {
    boolean testPassed = true;

    // Add some units.
    Unit apc = addUnit(testMap, testCo1, UnitModel.TRANSPORT, 1, 3);
    Unit mech = addUnit(testMap, testCo1, UnitModel.MECH, 1, 4);
    Unit mech2 = addUnit(testMap, testCo1, UnitModel.MECH, 3, 3);
    Unit recon = addUnit(testMap, testCo1, UnitModel.RECON, 1, 8); // On the HQ

    // Take away ammo/fuel.
    mech.ammo = 0;
    mech2.ammo = 0;
    apc.fuel = apc.model.maxFuel / 2;
    mech.fuel = 0;
    mech2.fuel = 0;
    recon.fuel = 0;

    // Double-check the units are out of bullets/gas.
    testPassed &= validate(mech.fuel == 0, "    Mech still has fuel, but shouldn't.");
    testPassed &= validate(mech2.fuel == 0, "    Mech2 still has fuel, but shouldn't.");
    testPassed &= validate(recon.fuel == 0, "    Recon still has fuel, but shouldn't.");
    testPassed &= validate((mech.ammo == 0),
        "    Mech still has " + mech.ammo + " ammo, but should be empty.");
    testPassed &= validate((mech2.ammo == 0),
        "    Mech2 weapon still has " + mech2.ammo + " ammo, but should be empty.");

    // Simulate a new turn for the APC/Recon; the apc should re-supply the mech, and the recon should re-supply from the HQ.
    GameEventQueue events = new GameEventQueue();
    events.addAll(apc.initTurn(testMap));
    events.addAll(recon.initTurn(testMap));
    events.addAll(mech.initTurn(testMap));
    events.addAll(mech2.initTurn(testMap));
    for( GameEvent event : events )
    {
      event.performEvent(testMap);
    }

    // Give the APC a new GameAction to go resupply mech2.
    GameAction resupplyAction = new ResupplyLifecycle.ResupplyAction(apc, Utils.findShortestPath(apc, 2, 3, testMap));
    performGameAction(resupplyAction, testGame);

    // Make sure the mechs got their mojo back.
    testPassed &= validate(apc.fuel != apc.model.maxFuel, "    APC resupplied itself. Life doesn't work that way.");
    testPassed &= validate(mech.fuel == mech.model.maxFuel, "    Mech should have max fuel after turn init, but doesn't.");
    testPassed &= validate(mech2.fuel == mech2.model.maxFuel, "    Mech2 should have max fuel after resupply, but doesn't.");
    testPassed &= validate(recon.fuel == recon.model.maxFuel, "    Recon should have max fuel after new turn, but doesn't.");
    testPassed &= validate((mech.ammo == mech.model.maxAmmo), "    Mech should have max ammo after resupply.");
    testPassed &= validate((mech2.ammo == mech2.model.maxAmmo), "    Mech2 should have max ammo after resupply.");

    // Clean up.
    testMap.removeUnit(apc);
    testMap.removeUnit(mech);
    testMap.removeUnit(mech2);
    testMap.removeUnit(recon);

    return testPassed;
  }

  private boolean testUnitJoinEvent()
  {
    boolean testPassed = true;

    // Add some units.
    Unit recipient = addUnit(testMap, testCo1, UnitModel.MECH, 1, 4);
    Unit donor = addUnit(testMap, testCo1, UnitModel.MECH, 1, 5);
    recipient.initTurn(testMap);
    donor.initTurn(testMap);

    // Record CO funds.
    int funds = testCo1.army.money;

    // Hurt the recipient.
    recipient.damageHP(2);

    // Verify health and readiness.
    testPassed &= validate(recipient.getHP() == 8, "    Recipient has incorrect HP!");
    testPassed &= validate(!recipient.isTurnOver, "    Donor's turn is over despite not having moved!");
    testPassed &= validate(!donor.isTurnOver, "    Recipient's turn is over despite not having moved!");

    // Tell Donor to join Recipient.
    GameAction joinAction = new JoinLifecycle.JoinAction(testMap, donor, Utils.findShortestPath(donor, new XYCoord(1, 4), testMap));
    performGameAction(joinAction, testGame);

    // Verification:
    // 1) Only the Recipient should still be on the map.
    testPassed &= validate(testMap.isLocationValid(recipient.x, recipient.y), "    Recipient no longer thinks it is on the map after being joined!");
    testPassed &= validate(!testMap.isLocationValid(donor.x, donor.y), "    Donor still thinks it is on the map after joining!");
    testPassed &= validate( null != testMap.getLocation(1, 4).getResident(), "    Recipient is no longer on the map!");
    testPassed &= validate( null == testMap.getLocation(1, 5).getResident(), "    Donor is still on the map!");

    // 2) The Recipient now has 10 HP.
    testPassed &= validate( recipient.getHP() == 10, "    The Recipient was not healed by joining!");

    // 3) The Recipient's turn is over.
    testPassed &= validate( recipient.isTurnOver, "    The Recipient's turn is not over!");

    // 4) the CO gained funds.
    testPassed &= validate( funds < testCo1.army.money, "    The Commander gained no funds by joining!");

    // Clean up.
    testMap.removeUnit(recipient);
    testMap.removeUnit(donor);

    return testPassed;
  }

  private boolean testCommanderDefeatEvent()
  {
    boolean testPassed = true;

    // Make sure our target CO is not defeated already.
    testPassed &= validate(testCo2.army.isDefeated == false, "    testCo2 started out in defeat, but he should not be.");

    // We loaded Firing Range, so we expect a city at location (2, 2)
    Terrain.MapLocation city = testMap.getLocation(10, 1);
    // ... an HQ at location (13, 1)
    Terrain.MapLocation hq = testMap.getLocation(13, 1);
    // ... and two factories at (13, 2) and (12, 2).
    Terrain.MapLocation fac1 = testMap.getLocation(13, 2);
    Terrain.MapLocation fac2 = testMap.getLocation(12, 2);

    // Verify the map looks as we expect.
    testPassed &= validate(city.getEnvironment().terrainType == TerrainType.CITY, "    No city at (10, 1).");
    city.setOwner(testCo2);
    testPassed &= validate(city.getOwner() == testCo2, "    City should belong to CO 2.");
    testPassed &= validate(hq.getEnvironment().terrainType == TerrainType.HEADQUARTERS, "    No HQ where expected.");
    testPassed &= validate(hq.getOwner() == testCo2, "    HQ should belong to CO 2.");
    testPassed &= validate(fac1.getEnvironment().terrainType == TerrainType.FACTORY, "    Fac 1 is not where expected.");
    testPassed &= validate(fac1.getOwner() == testCo2, "    Fac 1 should belong to CO 2.");
    testPassed &= validate(fac2.getEnvironment().terrainType == TerrainType.FACTORY, "    Fac 2 is not where expected.");
    testPassed &= validate(fac2.getOwner() == testCo2, "    Fac 2 should belong to CO 2.");

    // Grant some units to testCo2
    Unit baddie1 = addUnit(testMap, testCo2, UnitModel.TROOP, 13, 1);
    Unit baddie2 = addUnit(testMap, testCo2, UnitModel.MECH, 12, 1);
    Unit baddie3 = addUnit(testMap, testCo2, UnitModel.TRANSPORT, 13, 2);

    // Verify the units were added correctly.
    testPassed &= validate(testMap.getLocation(13, 1).getResident() == baddie1, "    Unit baddie1 is not where he belongs.");
    testPassed &= validate(baddie1.x == 13 && baddie1.y == 1, "    Unit baddie1 doesn't know where he is.");
    testPassed &= validate(testMap.getLocation(12, 1).getResident() == baddie2, "    Unit baddie2 is not where he belongs.");
    testPassed &= validate(baddie2.x == 12 && baddie2.y == 1, "    Unit baddie2 doesn't know where he is.");
    testPassed &= validate(testMap.getLocation(13, 2).getResident() == baddie3, "    Unit baddie3 is not where he belongs.");
    testPassed &= validate(baddie3.x == 13 && baddie3.y == 2, "    Unit baddie3 doesn't know where he is.");

    // Bring to pass this poor army's defeat.
    ArmyDefeatEvent event = new ArmyDefeatEvent(testCo2.army);
    event.performEvent(testMap);

    //================================ Validate post-conditions.

    // All of testCo2's Units should be removed from the map.
    testPassed &= validate(testMap.getLocation(13, 1).getResident() == null,
        "    Unit baddie1 was not removed from the map after defeat.");
    testPassed &= validate(baddie1.x == -1 && baddie1.y == -1, "    Unit baddie1 still thinks he's on the map after defeat.");
    testPassed &= validate(testMap.getLocation(12, 1).getResident() == null,
        "    Unit baddie2 was not removed from the map after defeat.");
    testPassed &= validate(baddie2.x == -1 && baddie2.y == -1, "    Unit baddie2 still thinks he's on the map after defeat.");
    testPassed &= validate(testMap.getLocation(13, 2).getResident() == null,
        "    Unit baddie3 was not removed from the map after defeat.");
    testPassed &= validate(baddie3.x == -1 && baddie3.y == -1, "    Unit baddie3 still thinks he's on the map after defeat.");

    // Ensure testCo2 no longer owns properties.
    testPassed &= validate(city.getOwner() == null, "    City should no longer have an owner.");
    testPassed &= validate(hq.getOwner() == null, "    HQ should no longer have an owner.");
    testPassed &= validate(fac1.getOwner() == null, "    Fac 1 should no longer have an owner.");
    testPassed &= validate(fac2.getOwner() == null, "    Fac 2 should no longer have an owner.");

    // His former HQ should now be a mere city.
    testPassed &= validate(hq.getEnvironment().terrainType == TerrainType.CITY,
        "    HQ was not downgraded to city after defeat.");

    // Confirm that yea, verily, testCo2 is truly defeated.
    testPassed &= validate(true == testCo2.army.isDefeated, "    testCo2 does not think he is defeated (but he so is).");

    // No cleanup should be required.
    return testPassed;
  }
}
