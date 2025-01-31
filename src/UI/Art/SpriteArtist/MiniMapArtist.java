package UI.Art.SpriteArtist;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import Engine.XYCoord;
import Terrain.MapInfo;
import UI.UIUtils;
import UI.UIUtils.COSpriteSpec;
import UI.UIUtils.Faction;

public class MiniMapArtist
{
  private static MapInfo lastMapDrawn;
  private static Color[] lastTeamColors;
  private static BufferedImage lastFullMapImage;

  /**
   * Retrieve a BufferedImage with a 1-pixel-per-tile representation of the provided MapInfo.
   * Requested images are generated and store on first call, then simply fetched and returned
   * if this function is called again with the same MapInfo.
   * Teams will be colored according to the default color ordering.
   */
  public static BufferedImage getMapImage(MapInfo mapInfo, int maxWidth, int maxHeight)
  {
    return getMapImage(mapInfo, UIUtils.getCOColors(), maxWidth, maxHeight);
  }

  /**
   * Retrieve a BufferedImage with a 1-pixel-per-tile representation of the provided MapInfo.
   * Requested images are generated and store on first call, then simply fetched and returned
   * if this function is called again with the same MapInfo.
   * @param teamColors A set of colors to be used for drawing each team. If insufficient colors
   *        are provided, black will be used for any remaining ones. If null is passed in,
   *        the default team-color ordering will be used.
   */
  public static BufferedImage getMapImage(MapInfo mapInfo, Color[] teamColors, int maxWidth, int maxHeight)
  {
    if( mapInfo != lastMapDrawn || palettesDiffer(teamColors) )
    {
      // If we don't already have an image, generate and store it for later.
      lastFullMapImage = generateFullMapImage(mapInfo, teamColors);
      lastMapDrawn = mapInfo;
      lastTeamColors = teamColors;
    }

    BufferedImage miniMap = lastFullMapImage;
    // Crunch it down a bit if it won't fit
    final int mapHeight = lastFullMapImage.getHeight();
    final int mapWidth = lastFullMapImage.getWidth();
    if(   maxHeight < mapHeight
        || maxWidth < mapWidth )
    {
      final double heightRatio = ((float)maxHeight) / mapHeight;
      final double widthRatio  = ((float)maxWidth ) / mapWidth;
      final double finalRatio  = Math.min(heightRatio, widthRatio);
      final int finalHeight    = (int) (mapHeight * finalRatio);
      final int finalWidth     = (int) (mapWidth * finalRatio);

      miniMap = SpriteLibrary.createTransparentSprite(finalWidth, finalHeight);

      final AffineTransform at = AffineTransform.getScaleInstance(finalRatio, finalRatio);
      final AffineTransformOp ato = new AffineTransformOp(at, AffineTransformOp.TYPE_BICUBIC);
      miniMap = ato.filter(lastFullMapImage, miniMap);
    }

    return miniMap;
  }

  /**
   * Generates a BufferedImage containing the full-scale map.
   */
  private static BufferedImage generateFullMapImage(MapInfo mapInfo, Color[] teamColors)
  {
    // If we don't have a palette yet, just get the default one from UIUtis.
    if( null == teamColors ) teamColors = UIUtils.getCOColors();

    BufferedImage image = new BufferedImage(
        mapInfo.getWidth() * SpriteLibrary.baseSpriteSize,
        mapInfo.getHeight() * SpriteLibrary.baseSpriteSize,
        BufferedImage.TYPE_INT_ARGB);
    Graphics g = image.getGraphics();

    for(int y = 0; y < mapInfo.getHeight(); ++y) // Iterate horizontally to layer terrain correctly.
    {
      for(int x = 0; x < mapInfo.getWidth(); ++x)
      {
        XYCoord coord = new XYCoord(x, y);
        COSpriteSpec spec = null;
        // Figure out team color, if any
        for( int co = 0; co < mapInfo.COProperties.length; ++co )
        {
          for( int i = 0; i < mapInfo.COProperties[co].length; ++i )
          {
            if( coord.equals(mapInfo.COProperties[co][i]) )
            {
              Color coColor;

              // Log a warning if SpriteLibrary doesn't have enough colors to support this map.
              if( co >= teamColors.length )
              {
                System.out.println("WARNING! '" + mapInfo.mapName + "' has more start locations than there are team colors!");

                // But soldier onwards anyway.
                coColor = Color.BLACK;
              }
              else
              {
                coColor = teamColors[co];
              }

              spec = new COSpriteSpec(new Faction(), coColor);
              break;
            }
          }
          if( null != spec)
            break;
        }

        // Fetch the relevant sprite set for this terrain type and have it draw itself.
        TerrainSpriteSet spriteSet = SpriteLibrary.getTerrainSpriteSet(mapInfo.terrain[x][y], spec);
        spriteSet.drawTerrain(g, mapInfo, x, y, false);
        spriteSet.drawTerrainObject(g, mapInfo, x, y, false);
      }
    }

    return image;
  }

  private static boolean palettesDiffer(Color[] newColors)
  {
    if( null == lastTeamColors || null == newColors )
      return true;
    if( lastTeamColors.length != newColors.length )
      return true;

    boolean colorsDiffer = false;
    for( int i = 0; i < newColors.length; ++i )
    {
      if( !newColors[i].equals(lastTeamColors[i]) )
      {
        colorsDiffer = true;
        break;
      }
    }

    return colorsDiffer;
  }
}
