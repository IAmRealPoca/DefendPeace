package UI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

import AI.AILibrary;
import CommandingOfficers.CommanderInfo;
import CommandingOfficers.CommanderLibrary;
import Engine.ConfigUtils;
import Engine.Driver;
import Engine.GameInstance;
import Engine.IController;
import Engine.IView;
import Engine.MapController;
import Engine.OptionSelector;
import UI.InputHandler.InputAction;
import UI.PlayerSetupInfo.CODeets;

/**
 * Controller for choosing COs and colors after the map has been chosen.
 * Left/Right changes the CO selector with focus.
 * Up/Down changes the CO selected for that slot.
 */
public class PlayerSetupController implements IController
{
  // This OptionSelector determines which player we have under the cursor.
  private OptionSelector playerSelector;
  private OptionSelector categorySelector;

  private GameBuilder gameBuilder = null;
  PlayerSetupInfo[] coSelectors;

  public enum SelectionCategories { COMMANDER, COLOR_FACTION, TEAM, AI, START };

  private IController subMenu;

  public PlayerSetupController( GameBuilder builder )
  {
    // Once we hit go, we plug all the COs we chose into our gameBuilder.
    gameBuilder = builder;

    // Set up our row/col selectors.
    int numCos = gameBuilder.mapInfo.getNumCos();
    playerSelector = new OptionSelector(numCos);
    categorySelector = new OptionSelector(SelectionCategories.values().length);
    categorySelector.setSelectedOption(SelectionCategories.START.ordinal()); // Best case is that no changes are needed.

    // Create objects to keep track of the selected options for each player.
    coSelectors = new PlayerSetupInfo[numCos];

    // Read in the last settings we used on this map, if available
    HashMap<Integer, String> optionMap = new HashMap<Integer, String>();
    ConfigUtils.readConfigLists(buildSettingsFileName(),
                                (String s)->Integer.valueOf(s),
                                (Scanner linescan)->linescan.nextLine(),
                                optionMap);

    final boolean flySolo = !builder.tagMode.supportsMultiCmdrSelect;
    // Start by making default CO/color selections.
    for(int co = 0; co < numCos; ++co)
    {
      // Set up our option selection framework
      coSelectors[co] = new PlayerSetupInfo( co, gameBuilder.mapInfo,
                               CommanderLibrary.getCommanderList(),
                               UIUtils.getCOColors(), UIUtils.getFactions(),
                               AILibrary.getAIList(),
                               optionMap.get(co) );

      // Enforce single-CO play if necessary
      final ArrayList<CODeets> coList = coSelectors[co].coList;
      if( flySolo && coList.size() > 1 )
      {
        CODeets lonely = coList.get(0);
        coList.clear();
        coList.add(lonely);
      }
    }
  }

  @Override
  public boolean handleInput(InputAction action)
  {
    boolean exitMenu = false;
    if(null != subMenu)
    {
      boolean exitSub = subMenu.handleInput(action);
      if(exitSub)
      {
        // The sub-menu will have applied any necessary player changes already.
        subMenu = null;
      }
    }
    else
    {
      // No sub-menu is active - handle the input here.
      exitMenu = handlePlayerSetupInput(action);
    }
    return exitMenu;
  }

  /** Returns the currently-active sub-menu, or null if control is held locally. */
  public IController getSubMenu()
  {
    return subMenu;
  }

  private boolean handlePlayerSetupInput(InputAction action)
  {
    boolean exitMenu = false;

    switch(action)
    {
      case SELECT:
        // Open a sub-menu based on which player attribute is selected, or start the game.
        if( categorySelector.getSelectionNormalized() == SelectionCategories.COMMANDER.ordinal() )
        {
          ArrayList<CommanderInfo> infos = CommanderLibrary.getCommanderList();
          subMenu = new PlayerSetupCommanderController(infos, getPlayerInfo(playerSelector.getSelectionNormalized()), gameBuilder.tagMode);
        }
        else if( categorySelector.getSelectionNormalized() == SelectionCategories.COLOR_FACTION.ordinal() )
        {
          subMenu = new PlayerSetupColorFactionController(getPlayerInfo(playerSelector.getSelectionNormalized()), getIconicUnit());
        }
        else if( categorySelector.getSelectionNormalized() == SelectionCategories.TEAM.ordinal() )
        {
          subMenu = new PlayerSetupTeamController(coSelectors, playerSelector.getSelectionNormalized());
        }
        else if( categorySelector.getSelectionNormalized() == SelectionCategories.AI.ordinal() )
        {
          subMenu = new PlayerSetupAiController(getPlayerInfo(playerSelector.getSelectionNormalized()));
        }
        else // ( categorySelector.getSelectionNormalized() == SelectionCategories.START.ordinal() )
        {
          /////////////////////////////////////////////////////////////////////////////////////////////
          // We have locked in our selection. Stuff it into the GameBuilder and then kick off the game.
          GameInstance newGame = gameBuilder.createGame(coSelectors);

          if( null != newGame )
          {
            // Save these settings for next time
            if( !ConfigUtils.writeConfigStrings(buildSettingsFileName(), coSelectors) )
              System.out.println("Unable to write player setup options to file.");

            MapView mv = Driver.getInstance().gameGraphics.createMapView(newGame);
            MapController mapController = new MapController(newGame, mv);

            // Mash the big red button and start the game.
            Driver.getInstance().changeGameState(mapController, mv);
          }
          else
          {
            System.out.println("WARNING! Something went wrong while creating the game!");
          }

          exitMenu = true;
        }
        break;
      case BACK:
        exitMenu = true;
        break;
      case DOWN:
      case UP:
        // Move to the next/previous player panel, but keep focus on the same category to maintain continuity.
        // i.e., if we have player 3's "team" attribute selected, hitting DOWN should move to player 4's "team" attribute.
        playerSelector.handleInput(action);
        break;
      case LEFT:
      case RIGHT:
        categorySelector.handleInput(action);
        break;
      case SEEK:
        ArrayList<CommanderInfo> infos = new ArrayList<CommanderInfo>();
        for( PlayerSetupInfo info : coSelectors)
          infos.addAll(info.getCurrentCOList());
        CO_InfoController coInfoMenu = new CO_InfoController(infos, playerSelector.getSelectionNormalized());
        IView infoView = Driver.getInstance().gameGraphics.createInfoView(coInfoMenu);

        // Give the new controller/view the floor
        Driver.getInstance().changeGameState(coInfoMenu, infoView);
        break;
        default:
          System.out.println("Warning: Unsupported input " + action + " in CO setup menu.");
    }
    return exitMenu;
  }

  public int getHighlightedPlayer()
  {
    return playerSelector.getSelectionNormalized();
  }

  public int getHighlightedCategory()
  {
    return categorySelector.getSelectionNormalized();
  }

  public PlayerSetupInfo getPlayerInfo(int p)
  {
    return coSelectors[p];
  }

  public String getIconicUnit()
  {
    return gameBuilder.unitModelScheme.getIconicUnitName();
  }

  private String buildSettingsFileName()
  {
    return "res/map_options/" + gameBuilder.mapInfo.mapName + "_army_selections.txt";
  }
}
