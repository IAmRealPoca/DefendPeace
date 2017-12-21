package Units;

import java.util.ArrayList;
import java.util.Vector;

import CommandingOfficers.Commander;
import Engine.GameAction;
import Engine.Utils;
import Terrain.GameMap;
import Terrain.Location;
import Units.UnitModel.UnitEnum;
import Units.Weapons.Weapon;

public class Unit
{
  public Vector<Unit> heldUnits;
  public UnitModel model;
  public int x;
  public int y;
  public int fuel;
  private int captureProgress;
  private Location captureTarget;
  public Commander CO;
  public boolean isTurnOver;
  private double HP;
  public Weapon[] weapons;

  public Unit(Commander co, UnitModel um)
  {
    CO = co;
    model = um;
    fuel = model.maxFuel;
    isTurnOver = true;
    HP = model.maxHP;
    captureProgress = 0;
    captureTarget = null;
    if( um.weaponModels != null )
    {
      weapons = new Weapon[um.weaponModels.length];
      for( int i = 0; i < um.weaponModels.length; i++ )
      {
        weapons[i] = new Weapon(um.weaponModels[i]);
      }
    }
    if( model.holdingCapacity > 0 )
      heldUnits = new Vector<Unit>(model.holdingCapacity);
  }

  public void initTurn(Location locus)
  {
    // Only perform turn initialization for the unit if it is on the map.
    //   Units that are e.g. in a transport don't burn fuel, etc.
    if( null != locus )
    {
      isTurnOver = false;
      fuel -= model.idleFuelBurn;
      if( captureTarget != null && captureTarget.getResident() != this )
      {
        captureTarget = null;
        captureProgress = 0;
      }
      if( HP < model.maxHP )
      {
        if( model.canRepairOn(locus) && locus.getOwner() == CO )
        {
          int neededHP = Math.min(model.maxHP - getHP(), 2); // will be 0, 1, 2
          double proportionalCost = model.moneyCost / model.maxHP;
          if( CO.money >= neededHP * proportionalCost )
          {
            CO.money -= neededHP * proportionalCost;
            alterHP(2);
          }
          else if( CO.money >= proportionalCost )
          {
            // case will only be used if neededHP is 2
            CO.money -= proportionalCost;
            alterHP(1);
          }
        }
      }
    }
  }

  /**
   * @return the base damage this unit would do against the specified target,
   * at the specified range, based on whether it will move before striking
   * Chooses the first weapon on the list that can deal damage.
   */
  public double getBaseDamage(UnitModel target, int range, boolean moved)
  {
    // if we have no weapons, we can't hurt things
    if( weapons == null )
      return 0;
    for( int i = 0; i < weapons.length; i++ )
    {
      // If the weapon isn't mobile, we shouldn't be able to fire if we moved.
      if( moved && !weapons[i].model.canFireAfterMoving )
      {
        continue;
      }
      double damage = weapons[i].getDamage(target, range);
      if( damage != 0 )
      {
        return damage;
      }
    }
    return 0;
  }

  // for the purpose of letting the unit know it has attacked.
  public void fire(final Unit defender)
  {
    UnitEnum target = defender.model.type;
    int i = 0;
    for( ; i < weapons.length; i++ )
    {
      if( weapons[i].getDamage(target) != 0 )
      {
        break;
      }
    }
    if( i == weapons.length )
      System.out.println("In " + model.name + "'s fire(): no valid weapon found");
    weapons[i].fire();
  }

  public int getHP()
  {
    return (int) Math.ceil(HP);
  }
  public double getPreciseHP()
  {
    return HP;
  }

  public void damageHP(double damage)
  {
    HP -= damage;
    if( HP < 0 )
    {
      HP = 0;
    }
  }
  public void alterHP(int change)
  {
    // Change the unit's health, but don't grant more
    // than 10 HP, and don't drop HP to zero.
    HP = Math.max(1, Math.min(10, getHP() + change));
  }

  public void capture(Location target)
  {
    if( !target.isCaptureable() )
    {
      System.out.println("ERROR! Attempting to capture an uncapturable Location!");
      return;
    }
    if( target.getOwner() == CO )
    {
      System.out.println("WARNING! Attempting to capture a property we own!");
      return;
    }

    if( target != captureTarget )
    {
      captureTarget = target;
      captureProgress = 0;
    }
    captureProgress += getHP();
    if( captureProgress >= 20 )
    {
      target.setOwner(CO);
      captureProgress = 0;
      target = null;
    }
  }

  public void stopCapturing()
  {
    captureTarget = null;
    captureProgress = 0;
  }

  public int getCaptureProgress()
  {
    return captureProgress;
  }

  // Removed for the forseeable future; may be back
  /*	public static double getAttackPower(final CombatParameters params) {
  //		double output = model
  //		return [B*ACO/100+R]*(AHP/10)*[(200-(DCO+DTR*DHP))/100] ;
  		return 0;
  	}
  	public static double getDefensePower(Unit unit, boolean isCounter) {
  		return 0;
  	}*/

  /** Compiles and returns a list of all actions this unit could perform on map from location (xLoc, yLoc). */
  public ArrayList<GameAction.ActionType> getPossibleActions(GameMap map, int xLoc, int yLoc)
  {
    ArrayList<GameAction.ActionType> actions = new ArrayList<GameAction.ActionType>();
    if( map.isLocationEmpty(this, xLoc, yLoc) )
    {
      for( int i = 0; i < model.possibleActions.length; i++ )
      {
        switch (model.possibleActions[i])
        {
          case ATTACK:
            // highlight the tiles in range, and check them for targets
            Utils.findActionableLocations(this, GameAction.ActionType.ATTACK, xLoc, yLoc, map);
            boolean found = false;
            for( int w = 0; w < map.mapWidth; w++ )
            {
              for( int h = 0; h < map.mapHeight; h++ )
              {
                if( map.getLocation(w, h).isHighlightSet() )
                {
                  actions.add(GameAction.ActionType.ATTACK);
                  found = true;
                  break; // We just need one target to know attacking is possible.
                }
              }
              if( found )
                break;
            }
            map.clearAllHighlights();
            break;
          case CAPTURE:
            if( map.getLocation(xLoc, yLoc).getOwner() != CO && map.getLocation(xLoc, yLoc).isCaptureable() )
            {
              actions.add(GameAction.ActionType.CAPTURE);
            }
            break;
          case WAIT:
            actions.add(GameAction.ActionType.WAIT);
            break;
          case LOAD:
            // Don't add - there's no unit there to board.
            break;
          case UNLOAD:
            if( heldUnits.size() > 0 )
            {
              actions.add(GameAction.ActionType.UNLOAD);
            }
            break;
          default:
            System.out
                .println("getPossibleActions: Invalid action in model's possibleActions[" + i + "]: " + model.possibleActions[i]);
        }
      }
    }
    else
    // There is a unit in the space we are evaluating. Only Load actions are supported in this case.
    {
      actions.add(GameAction.ActionType.LOAD);
    }

    return actions;
  }

  public boolean hasCargoSpace(UnitEnum type)
  {
    return (model.holdingCapacity > 0 && heldUnits.size() < model.holdingCapacity && model.holdables.contains(type));
  }
}
