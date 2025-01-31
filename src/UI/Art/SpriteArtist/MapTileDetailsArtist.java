package UI.Art.SpriteArtist;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import Engine.GameEvents.GameEventListener.CacheInvalidationListener;
import Engine.GameInstance;
import Engine.XYCoord;
import Terrain.GameMap;
import Terrain.MapLocation;
import Terrain.TerrainType;
import Units.Unit;

/**
 * Generates an overlay image to show details about the unit and terrain under the cursor.
 */
public class MapTileDetailsArtist
{
  private static XYCoord currentTile = new XYCoord(-1, -1);
  private static BufferedImage tileOverlay;
  private static MtdaListener mtdaListener = new MtdaListener();

  public static void register(GameInstance gi)
  {
    mtdaListener.registerForEvents(gi);
  }

  public static void resetOverlay()
  {
    currentTile = new XYCoord(-1, -1);
  }

  public static void drawTileDetails(Graphics g, GameMap map, XYCoord tileToDetail, boolean overlayIsLeft)
  {
    // Rebuild the overlay image if needed.
    generateOverlay(map, tileToDetail);

    int edgeBuffer = 2;
    int mapViewHeight = SpriteOptions.getScreenDimensions().height / SpriteOptions.getDrawScale();
    int mapViewWidth = SpriteOptions.getScreenDimensions().width / SpriteOptions.getDrawScale();
    if( overlayIsLeft )
    { // Draw the overlay on the left side.
      int drawX = edgeBuffer;
      int drawY = mapViewHeight - edgeBuffer - tileOverlay.getHeight();
      g.drawImage(tileOverlay, drawX, drawY, null);
    }
    else
    { // Draw the overlay on the right side.
      int drawX = mapViewWidth - edgeBuffer - tileOverlay.getWidth();
      int drawY = mapViewHeight - edgeBuffer - tileOverlay.getHeight();
      g.drawImage(tileOverlay, drawX, drawY, null);
    }
  }

  private static void generateOverlay(GameMap map, XYCoord coord)
  {
    if( currentTile.equals(coord) )
      return; // Did this already; just use the cached image.

    // Define useful quantities.
    int tileSize = SpriteLibrary.baseSpriteSize;
    int iconSize = SpriteLibrary.baseSpriteSize/2;
    MapLocation loc = map.getLocation(coord);
    TerrainType terrain = loc.getEnvironment().terrainType;
    boolean isTerrainObject = TerrainSpriteSet.isTerrainObject(terrain);
    Unit unit = loc.getResident();

    // Get the terrain image to draw.
    TerrainSpriteSet tss = SpriteLibrary.getTerrainSpriteSet(loc);
    int terrainSubIndex = TerrainSpriteSet.getTileVariation(coord.xCoord, coord.yCoord);
    BufferedImage terrainSprite = tss.getTerrainSprite().getFrame(terrainSubIndex);

    // Collect terrain attributes to draw.
    ArrayList<AttributeArtist> terrainAttrs = new ArrayList<AttributeArtist>();
    terrainAttrs.add(new AttributeArtist(SpriteLibrary.MapIcons.SHIELD.getIcon(), terrain.getDefLevel()));
    if( loc.durability < 99 ) terrainAttrs.add(new AttributeArtist(SpriteLibrary.MapIcons.HEART.getIcon(), loc.durability));

    // Get the unit image.
    ArrayList<AttributeArtist> unitAttrs = new ArrayList<AttributeArtist>();
    BufferedImage unitImage = null;
    if( null != unit )
    {
      UnitSpriteSet uss = SpriteLibrary.getMapUnitSpriteSet(unit);
      unitImage = uss.getUnitImage();

      unitAttrs.add(new AttributeArtist(SpriteLibrary.MapIcons.HEART.getIcon(), unit.getHP()));
      if( unit.model.needsFuel() )
        unitAttrs.add(new AttributeArtist(SpriteLibrary.MapIcons.FUEL.getIcon(), unit.fuel));
      if( unit.ammo >= 0 ) 
        unitAttrs.add(new AttributeArtist(SpriteLibrary.MapIcons.AMMO.getIcon(), unit.ammo));
      if( unit.getCaptureProgress() > 0)
        unitAttrs.add(new AttributeArtist(SpriteLibrary.getCaptureIcon(unit.CO.myColor),
            map.getEnvironment(coord).terrainType.getCaptureThreshold()-unit.getCaptureProgress()));
    }

    ///////////////////////////////////////////////////////////////
    // Calculate the size of the panel.
    int terrainPanelW = tileSize * 3;
    int unitPanelW = (unitAttrs.isEmpty() ? 0 : (tileSize * 2));
    int panelW = terrainPanelW + unitPanelW;
    
    // Each attribute with a 1-px buffer, plus space for the tile itself.
    int numAttrs = Math.max(terrainAttrs.size(), unitAttrs.size());
    int bufferPx = 3;
    int panelH = numAttrs*iconSize+numAttrs+(tileSize*2)+bufferPx;

    // Create the overlay image.
    tileOverlay = SpriteLibrary.createTransparentSprite(panelW, panelH);
    Graphics ltog = tileOverlay.getGraphics();

    // Draw the semi-transparent panel backing.
    ltog.setColor(new Color(0, 0, 0, 100));
    //ltog.fillRect(0, 0, tileOverlay.getWidth(), tileOverlay.getHeight());
    ltog.fillRoundRect(0, 0, tileOverlay.getWidth(), tileOverlay.getHeight(), bufferPx*2, bufferPx*2);

    // Figure out where to draw.
    int drawX = isTerrainObject ? 0 : tileSize;
    int drawY = isTerrainObject ? 0 : tileSize;

    // Draw all the terrain stuff.
    drawColumn(ltog, terrainSprite, terrainAttrs, drawX, drawY, isTerrainObject ? iconSize : -iconSize);

    // Draw all the unit stuff.
    if( null != unitImage )
    {
      drawX = terrainPanelW;
      drawY = tileSize;
      drawColumn(ltog, unitImage, unitAttrs, drawX, drawY, -iconSize/2);
    }

    currentTile = coord;
  }

  private static void drawColumn(Graphics g, BufferedImage image, ArrayList<AttributeArtist> attrs, int drawX, int drawY, int bumpAmt)
  {
    int iconSize = SpriteLibrary.baseSpriteSize/2;
    g.drawImage(image, drawX, drawY, null);
    drawX += bumpAmt;
    drawY += image.getHeight();
    for( AttributeArtist aa : attrs )
    {
      drawY++;
      aa.draw(g, drawX, drawY);
      drawY += iconSize;
    }
  }

  private static class AttributeArtist
  {
    private BufferedImage icon;
    private Integer value;
    public AttributeArtist(BufferedImage image, Integer quantity)
    {
      icon = image;
      value = quantity;
    }
    public void draw(Graphics g, int drawX, int drawY)
    {
      g.drawImage(icon, drawX, drawY, null);
      drawX += icon.getWidth() + 2;
      Sprite numbers = SpriteLibrary.getMapUnitNumberSprites();
      int tensVal = (int)(value / 10);
      if( 0 != tensVal )
      {
        BufferedImage tens = numbers.getFrame(tensVal);
        g.drawImage(tens, drawX, drawY, null);
      }
      int onesVal = (int)(value % 10);
      BufferedImage ones = numbers.getFrame(onesVal);
      drawX += ones.getWidth();
      g.drawImage(ones, drawX, drawY, null);
    }
  }

  /** This class just listens for any event that could change what is under the cursor, which is pretty much all of them. */
  private static class MtdaListener implements CacheInvalidationListener
  {
    private static final long serialVersionUID = 1L;

    @Override
    public boolean shouldSerialize() { return false; }

    @Override
    public void InvalidateCache() { MapTileDetailsArtist.resetOverlay(); }
  }
}
