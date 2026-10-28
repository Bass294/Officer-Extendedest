package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

/** Small GL helpers for the respec menu. Each call sets the GL state it needs and leaves textures enabled. */
public class oee_Draw {

    public static void quad(float x, float y, float w, float h, Color c, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        setColor(c, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** A 1 px line, normal blending. */
    public static void line(float x1, float y1, float x2, float y2, Color c, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(1f);
        setColor(c, alpha);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** A 1 px additive line fading in from the ends to full alpha at its middle. */
    public static void gradientLine(float x1, float y1, float x2, float y2, Color c, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glLineWidth(1f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        setColor(c, 0f);
        GL11.glVertex2f(x1, y1);
        setColor(c, alpha);
        GL11.glVertex2f((x1 + x2) / 2f, (y1 + y2) / 2f);
        setColor(c, 0f);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** A sprite centered on (cx, cy); width/height below 0 keep the sprite's own size. */
    public static void sprite(SpriteAPI sprite, float cx, float cy, float w, float h, Color color, float alpha,
                              boolean additive) {
        if (sprite == null) return;
        if (additive) sprite.setAdditiveBlend(); else sprite.setNormalBlend();
        sprite.setColor(color);
        if (w >= 0 && h >= 0) sprite.setSize(w, h);
        sprite.setAlphaMult(alpha);
        sprite.renderAtCenter(cx, cy);
        sprite.setNormalBlend();
    }

    /**
     * getSprite only finds textures the game has already loaded; some (the large elite overlays) are loaded only when
     * a vanilla screen first uses them, so load the texture here when the sprite comes back empty.
     */
    public static SpriteAPI loadSprite(String path) {
        if (path == null || path.isEmpty()) return null;
        try {
            SpriteAPI sprite = Global.getSettings().getSprite(path);
            if (sprite == null || sprite.getTextureId() == 0 || sprite.getWidth() <= 0f) {
                Global.getSettings().loadTexture(path);
                sprite = Global.getSettings().getSprite(path);
            }
            return sprite;
        } catch (Exception e) {
            Global.getLogger(oee_Draw.class).warn("Officer Extendedest: couldn't load sprite " + path, e);
            return null;
        }
    }

    private static void setColor(Color c, float alpha) {
        float a = Math.max(0f, Math.min(1f, alpha)) * c.getAlpha();
        GL11.glColor4ub((byte) c.getRed(), (byte) c.getGreen(), (byte) c.getBlue(), (byte) a);
    }
}
