package CommandingOfficers.OrangeStar;

import Engine.GameScenario;
import Engine.Combat.BattleInstance.BattleParams;
import CommandingOfficers.Commander;
import CommandingOfficers.CommanderAbility;
import CommandingOfficers.CommanderInfo;
import Engine.GameEvents.GameEventQueue;
import Terrain.MapMaster;
import Units.Unit;

public class Caroline extends Commander
{
  private static final long serialVersionUID = 1L;
  private static final CommanderInfo coInfo = new instantiator();
  private static class instantiator extends CommanderInfo
  {
    private static final long serialVersionUID = 1L;
    public instantiator()
    {
      super("Caroline");
      infoPages.add(new InfoPage(
          "Caroline (rebalanced Nell)\r\n" +
          "D2D: Luck range of 0% to +15%.\n" +
          "Note: Luck floor is done via base damage 'cause I'm lazy\n" +
          "Meter: xxxXXX\n" +
          "COP: Luck floor is raised, for a luck range of +7% minimum to +15% maximum.\n" +
          "SCOP: Luck floor is maxed out, for +15% luck on every attack."));
    }
    @Override
    public Commander create(GameScenario.GameRules rules)
    {
      return new Caroline(rules);
    }
  }

  private final int luckMax = 15;
  private int luckFloor = 0;

  public Caroline(GameScenario.GameRules rules)
  {
    super(coInfo, rules);

    addCommanderAbility(new LuckFloor(this, "Lucky Star", 3, 7));
    addCommanderAbility(new LuckFloor(this, "Lady Luck", 6, 15));
  }

  public static CommanderInfo getInfo()
  {
    return coInfo;
  }

  @Override
  public GameEventQueue initTurn(MapMaster map)
  {
    this.luckFloor = 0;
    return super.initTurn(map);
  }

  @Override
  public void applyCombatModifiers(BattleParams params, boolean amITheAttacker)
  {
    Unit minion = null;
    if( params.attacker.CO == this )
    {
      minion = params.attacker;
    }

    if( null != minion )
    {
      params.luckMax = luckMax - luckFloor;
      params.baseDamage += luckFloor;
    }
  }

  private static class LuckFloor extends CommanderAbility
  {
    private static final long serialVersionUID = 1L;
    private int power;
    Caroline COcast;

    LuckFloor(Caroline commander, String name, int cost, int newFloor)
    {
      super(commander, name, cost);
      COcast = commander;
      power = newFloor;
    }

    @Override
    protected void perform(MapMaster gameMap)
    {
      COcast.luckFloor = power;
    }
  }
}

