package CommandingOfficers;

import CommandingOfficers.Modifiers.COMovementModifier;
import CommandingOfficers.Modifiers.UnitTypeDamageModifier;
import Terrain.MapMaster;
import Units.Unit;
import Units.UnitModel;
import Units.UnitModel.ChassisEnum;

public class GEJess extends Commander
{
  private static final CommanderInfo coInfo = new instantiator();
  private static class instantiator extends CommanderInfo
  {
    public instantiator()
    {
      super("Jess");
      infoPages.add(new InfoPage(
          "Jess\r\n" + 
          "  Vehicles gain +10% attack, but all other units (including footsoldiers) lose -10% attack\r\n" + 
          "Turbo Charge -- All units resupply; vehicles gain +10% attack and +1 Movement\r\n" + 
          "Overdrive -- All units resupply; vehicles gain +30% attack +2 Movement"));
    }
    @Override
    public Commander create()
    {
      return new GEJess();
    }
  }

  public GEJess()
  {
    super(coInfo);

    for( UnitModel um : unitModels )
    {
      if( um.chassis == ChassisEnum.TANK)
      {
        um.modifyDamageRatio(10);
      }
      else
      {
        um.modifyDamageRatio(-10);
      }
    }

    addCommanderAbility(new TurboCharge(this));
    addCommanderAbility(new Overdrive(this));
  }

  public static CommanderInfo getInfo()
  {
    return coInfo;
  }

  private static class TurboCharge extends CommanderAbility
  {
    private static final String NAME = "Turbo Charge";
    private static final int COST = 3;

    TurboCharge(Commander commander)
    {
      super(commander, NAME, COST);
    }

    @Override
    protected void perform(MapMaster gameMap)
    {
      for (Unit unit : myCommander.units)
      {
        unit.resupply();
      }
      UnitTypeDamageModifier landPowerMod = new UnitTypeDamageModifier(10);
      COMovementModifier landMoveMod = new COMovementModifier(1);
      for( UnitModel um : myCommander.unitModels )
      {
        if( um.chassis == ChassisEnum.TANK)
        {
          landPowerMod.addApplicableUnitModel(um);
          landMoveMod.addApplicableUnitModel(um);
        }
      }
      myCommander.addCOModifier(landPowerMod);
      myCommander.addCOModifier(landMoveMod);
    }
  }

  private static class Overdrive extends CommanderAbility
  {
    private static final String NAME = "Overdrive";
    private static final int COST = 6;

    Overdrive(Commander commander)
    {
      // as we start in Bear form, UpTurn is the correct starting name
      super(commander, NAME, COST);
    }

    @Override
    protected void perform(MapMaster gameMap)
    {
      for (Unit unit : myCommander.units)
      {
        unit.resupply();
      }
      UnitTypeDamageModifier landPowerMod = new UnitTypeDamageModifier(30);
      COMovementModifier landMoveMod = new COMovementModifier(2);
      for( UnitModel um : myCommander.unitModels )
      {
        if( um.chassis == ChassisEnum.TANK)
        {
          landPowerMod.addApplicableUnitModel(um);
          landMoveMod.addApplicableUnitModel(um);
        }
      }
      myCommander.addCOModifier(landPowerMod);
      myCommander.addCOModifier(landMoveMod);
    }
  }
}

