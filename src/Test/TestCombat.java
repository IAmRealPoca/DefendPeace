package Test;

import CommandingOfficers.Commander;
import CommandingOfficers.Patch;
import CommandingOfficers.Strong;
import Engine.Army;
import Engine.GameAction;
import Engine.GameInstance;
import Engine.GameScenario;
import Engine.Utils;
import Engine.GameEvents.ArmyDefeatEvent;
import Engine.GameEvents.GameEvent;
import Engine.GameEvents.GameEventQueue;
import Engine.UnitActionLifecycles.BattleLifecycle;
import Terrain.MapLibrary;
import Terrain.MapMaster;
import Units.Unit;
import Units.UnitModel;

public class TestCombat extends TestCase
{
  private static Commander testCo1;
  private static Commander testCo2;
  private static MapMaster testMap;
  private static GameInstance testGame;

  /** Make two COs and a MapMaster to use with this test case. */
  private void setupTest()
  {
    GameScenario scn = new GameScenario();
    testCo1 = new Strong(scn.rules);
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
    testPassed &= validate(testUnitDeath(), "  Unit death test failed.");
    testPassed &= validate(testTeamAttack(), "  Team attack test failed.");
    testPassed &= validate(testIndirectAttacks(), "  Indirect combat test failed.");
    testPassed &= validate(testMoveAttack(), "  Move-Attack test failed.");
    testPassed &= validate(testCounterAttack(), "  Counterattack test failed.");
    testPassed &= validate(testKillLastUnit(), "  Last-unit death test failed.");
    return testPassed;
  }

  /** Test that units actually die, and don't counter-attack when they are killed. */
  private boolean testUnitDeath()
  {
    // Add our combatants
    Unit mechA = addUnit(testMap, testCo1, UnitModel.MECH, 1, 1);
    Unit infB = addUnit(testMap, testCo2, UnitModel.TROOP, 1, 2);
    mechA.initTurn(testMap); // Make sure he is ready to move.

    // Make sure the infantry will die with one attack
    infB.damageHP(7);

    // Execute inf- I mean, the action.
    performGameAction(new BattleLifecycle.BattleAction(testMap, mechA, Utils.findShortestPath(mechA, 1, 1, testMap), 1, 2),
        testGame);

    // Check that the mech is undamaged, and that the infantry is no longer with us.
    boolean testPassed = validate(mechA.getPreciseHP() == 10, "    Attacker lost or gained health.");
    testPassed &= validate(testMap.getLocation(1, 2).getResident() == null, "    Defender is still on the map.");

    // Clean up
    testMap.removeUnit(mechA);

    return testPassed;
  }
  
  /** Test that attacking your friends doesn't work. */
  private boolean testTeamAttack()
  {
    // Add our combatants
    Unit mechA = addUnit(testMap, testCo1, UnitModel.MECH, 1, 1);
    Unit infB = addUnit(testMap, testCo2, UnitModel.TROOP, 1, 2);
    
    // Make them friends
    testCo1.army.team = 0;
    testCo2.army.team = 0;

    // Make sure the infantry will die with one attack
    infB.damageHP(7);

    // Hug the infantry in a friendly manner.
    performGameAction(new BattleLifecycle.BattleAction(testMap, mechA, Utils.findShortestPath(mechA, 1, 1, testMap), 1, 2),
        testGame);

    // Check that the mech is undamaged, and that the infantry is still with us.
    boolean testPassed = validate(mechA.getPreciseHP() == 10, "    Attacker lost or gained health.");
    testPassed &= validate(testMap.getLocation(1, 2).getResident() != null, "    Defender died.");

    // Clean up
    testMap.removeUnit(mechA);
    testMap.removeUnit(infB);
    testCo1.army.team = -1;
    testCo2.army.team = -1;

    return testPassed;
  }

  /** Test that weapon range works properly, and that immobile weapons cannot move and fire. */
  private boolean testIndirectAttacks()
  {
    // Add our combatants
    Unit offender = addUnit(testMap, testCo1, UnitModel.SIEGE, 6, 5);
    Unit defender = addUnit(testMap, testCo2, UnitModel.MECH, 6, 6);
    Unit victim = addUnit(testMap, testCo2, UnitModel.SIEGE, 6, 7);

    // offender will attempt to shoot point blank. This should fail, since artillery cannot direct fire.
    offender.initTurn(testMap); // Make sure he is ready to move.
    performGameAction(new BattleLifecycle.BattleAction(testMap, offender, Utils.findShortestPath(offender, 6, 5, testMap), 6, 6),
        testGame);
    boolean testPassed = validate(defender.getPreciseHP() == 10, "    Artillery dealt damage at range 1. Artillery range should be 2-3.");
    
    // offender will attempt to move and fire. This should fail, since artillery cannot fire after moving.
    offender.initTurn(testMap);
    performGameAction(new BattleLifecycle.BattleAction(testMap, offender, Utils.findShortestPath(offender, 6, 4, testMap), 6, 6),
        testGame);
    testPassed &= validate(defender.getPreciseHP() == 10, "    Artillery dealt damage despite moving before firing.");

    // offender will shoot victim.
    offender.initTurn(testMap); // Make sure he is ready to move.
    performGameAction(new BattleLifecycle.BattleAction(testMap, offender, Utils.findShortestPath(offender, 6, 5, testMap), 6, 7),
        testGame);
    testPassed &= validate(victim.getPreciseHP() != 10, "    Artillery failed to do damage at a range of 2, without moving.");
    testPassed &= validate(offender.getPreciseHP() == 10, "    Artillery received a counterattack from a range of 2. Counterattacks should only be possible at range 1.");

    // defender will attack offender.
    defender.initTurn(testMap); // Make sure he is ready to move.
    performGameAction(new BattleLifecycle.BattleAction(testMap, defender, Utils.findShortestPath(defender, 6, 6, testMap), 6, 5),
        testGame);
    
    // check that offender is damaged and defender is not.
    testPassed &= validate(offender.getPreciseHP() != 10, "    Mech failed to deal damage to adjacent artillery.");
    testPassed &= validate(defender.getPreciseHP() == 10, "    Mech receives a counterattack from artillery at range 1. Artillery range should be 2-3.");

    // Clean up
    testMap.removeUnit(offender);
    testMap.removeUnit(defender);
    testMap.removeUnit(victim);

    return testPassed;
  }

  /** Test that units can move and attack in one turn. */
  private boolean testMoveAttack()
  {
    // Add our combatants
    Unit attacker = addUnit(testMap, testCo1, UnitModel.TROOP, 1, 1);
    Unit defender = addUnit(testMap, testCo2, UnitModel.MECH, 1, 3);

    // Perform an attack.
    attacker.initTurn(testMap); // Make sure he is ready to move.
    performGameAction(new BattleLifecycle.BattleAction(testMap, attacker, Utils.findShortestPath(attacker, 1, 2, testMap), 1, 3), testGame);

    // Check that the both units were hurt in the exchange.
    boolean testPassed = validate(defender.getHP() < 10, "    Defender took no damage.");
    testPassed &= validate(attacker.getHP() < 10, "    Attacker took no damage.");

    // Clean up
    testMap.removeUnit(attacker);
    testMap.removeUnit(defender);
    testCo1.units.clear();
    testCo2.units.clear();

    return testPassed;
  }

  /** Test that units can move and attack in one turn. */
  private boolean testCounterAttack()
  {
    // Add our combatants
    Unit attacker = addUnit(testMap, testCo1, UnitModel.TANK | UnitModel.RECON, 1, 1);
    Unit defender = addUnit(testMap, testCo2, UnitModel.TANK | UnitModel.ASSAULT, 1, 3);
    System.out.println("Created " + attacker.toStringWithLocation() + " and " + defender.toStringWithLocation() );

    // Attack defender with attacker.
    attacker.initTurn(testMap); // Make sure he is ready to move.
    performGameAction(new BattleLifecycle.BattleAction(testMap, attacker, Utils.findShortestPath(attacker, 1, 2, testMap), 1, 3), testGame);

    // We don't expect the tank to be hurt or the recon to lose ammo, so just
    // Verify that the recon was hurt and the tank expended ammo.
    boolean testPassed = validate(attacker.getHP() < 10, "    Recon took no damage!");
    testPassed &= validate( defender.ammo == (defender.model.maxAmmo-1), "    Defender expended no ammo!" );

    // Clean up
    testMap.removeUnit(attacker);
    testMap.removeUnit(defender);
    testCo1.units.clear();
    testCo2.units.clear();

    return testPassed;
  }

  /** Make sure we generate a ArmyDefeatEvent when killing the final unit for a CO. */
  private boolean testKillLastUnit()
  {
    boolean testPassed = true;

    // Add our combatants
    Unit mechA = addUnit(testMap, testCo1, UnitModel.MECH, 1, 1);
    Unit infB = addUnit(testMap, testCo2, UnitModel.TROOP, 1, 2);

    // Make sure the infantry will die with one attack
    infB.damageHP(7);

    // Create the attack action so we can predict the unit will die, and his CO will therefore be defeated.
    mechA.initTurn(testMap); // Make sure he is ready to act.
    GameAction battleAction = new BattleLifecycle.BattleAction(testMap, mechA, Utils.findShortestPath(mechA, 1, 1, testMap), 1, 2);

    // Extract the resulting GameEventQueue.
    GameEventQueue events = battleAction.getEvents(testMap);

    // Make sure a ArmyDefeatEvent was generated as a result (the actual event test is in TestGameEvent.java).
    boolean hasDefeatEvent = false;
    for( GameEvent event : events )
    {
      if( event instanceof ArmyDefeatEvent )
      {
        hasDefeatEvent = true;
        break;
      }
    }
    testPassed &= validate( hasDefeatEvent, "    No ArmyDefeatEvent generated when losing final unit!");

    return testPassed;
  }
}
