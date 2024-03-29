/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.base.viewer2d;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;
import java.util.HashMap;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.editor.image.AnnotationsLayer;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.graphic.GraphicLabel;

/**
 * The Class InfoLayer.
 * 
 * @author Nicolas Roduit
 */
public class InfoLayer implements AnnotationsLayer {

    private final HashMap<String, Boolean> displayPreferences = new HashMap<String, Boolean>();
    private boolean visible = true;
    private final Color color = Color.yellow;
    private static final int BORDER = 10;
    private final DefaultView2d view2DPane;
    private String pixelInfo = ""; //$NON-NLS-1$
    private final Rectangle pixelInfoBound;
    private final Rectangle preloadingProgressBound;
    private int border = BORDER;
    private boolean showBottomScale = true;

    public InfoLayer(DefaultView2d view2DPane) {
        this.view2DPane = view2DPane;
        displayPreferences.put(ANNOTATIONS, true);
        displayPreferences.put(IMAGE_ORIENTATION, true);
        displayPreferences.put(SCALE, true);
        displayPreferences.put(LUT, false);
        displayPreferences.put(PIXEL, true);
        displayPreferences.put(WINDOW_LEVEL, true);
        displayPreferences.put(ZOOM, true);
        displayPreferences.put(ROTATION, false);
        displayPreferences.put(FRAME, true);
        this.pixelInfoBound = new Rectangle();
        this.preloadingProgressBound = new Rectangle();
    }

    @Override
    public boolean isShowBottomScale() {
        return showBottomScale;
    }

    @Override
    public void setShowBottomScale(boolean showBottomScale) {
        this.showBottomScale = showBottomScale;
    }

    @Override
    public void paint(Graphics2D g2) {
        ImageElement image = view2DPane.getImage();
        if (!visible || image == null) {
            return;
        }

        final Rectangle bound = view2DPane.getBounds();
        float midx = bound.width / 2f;
        float midy = bound.height / 2f;

        g2.setPaint(color);

        final float fontHeight = FontTools.getAccurateFontHeight(g2);
        final float midfontHeight = fontHeight * FontTools.getMidFontHeightFactor();
        float drawY = bound.height - border - 1.5f; // -1.5 for outline

        if (!image.isReadable()) {
            String message = Messages.getString("InfoLayer.error_msg"); //$NON-NLS-1$
            float y = midy;
            GraphicLabel.paintColorFontOutline(g2, message, midx - g2.getFontMetrics().stringWidth(message) / 2, y,
                Color.RED);
            String[] desc = image.getMediaReader().getReaderDescription();
            if (desc != null) {
                for (String str : desc) {
                    if (str != null) {
                        y += fontHeight;
                        GraphicLabel.paintColorFontOutline(g2, str, midx - g2.getFontMetrics().stringWidth(str) / 2, y,
                            Color.RED);
                    }
                }
            }
        }
        if (image.isReadable() && getDisplayPreferences(SCALE)) {
            drawScale(g2, bound, fontHeight);
        }
        if (image.isReadable() && getDisplayPreferences(LUT)) {
            drawLUT(g2, bound, midfontHeight);
        }

        if (getDisplayPreferences(PIXEL)) {
            String str = Messages.getString("InfoLayer.pix") + ": " + pixelInfo; //$NON-NLS-1$ //$NON-NLS-2$
            GraphicLabel.paintFontOutline(g2, str, border, drawY - 1);
            drawY -= fontHeight + 2;
            pixelInfoBound.setBounds(border - 2, (int) drawY + 3, g2.getFontMetrics().stringWidth(str) + 4,
                (int) fontHeight + 2);
            // g2.draw(pixelInfoBound);
        }
        if (getDisplayPreferences(WINDOW_LEVEL)) {
            GraphicLabel
                .paintFontOutline(
                    g2,
                    Messages.getString("InfoLayer.win") + ": " + view2DPane.getActionValue(ActionW.WINDOW.cmd()) //$NON-NLS-1$ //$NON-NLS-2$
                        + " " + Messages.getString("InfoLayer.level") + ": " + view2DPane.getActionValue(ActionW.LEVEL.cmd()), border, drawY); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            drawY -= fontHeight;
        }
        if (getDisplayPreferences(ZOOM)) {
            GraphicLabel
                .paintFontOutline(
                    g2,
                    Messages.getString("InfoLayer.zoom") + ": " + DecFormater.twoDecimal(view2DPane.getViewModel().getViewScale() * 100) + " " + Messages.getString("InfoLayer.percent"), border, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    drawY);
            drawY -= fontHeight;
        }
        if (getDisplayPreferences(ROTATION)) {
            GraphicLabel
                .paintFontOutline(
                    g2,
                    Messages.getString("InfoLayer.angle") + ": " + view2DPane.getActionValue(ActionW.ROTATION.cmd()) + " " + Messages.getString("InfoLayer.angle_symb"), border, drawY); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            drawY -= fontHeight;
        }

        if (getDisplayPreferences(FRAME)) {
            GraphicLabel
                .paintFontOutline(
                    g2,
                    Messages.getString("InfoLayer.frame") //$NON-NLS-1$
                        + ": " //$NON-NLS-1$
                        + (view2DPane.getFrameIndex() + 1)
                        + " / " //$NON-NLS-1$
                        + view2DPane.getSeries().size(
                            (Filter<ImageElement>) view2DPane.getActionValue(ActionW.FILTERED_SERIES.cmd())), border,
                    drawY);
            drawY -= fontHeight;
        }

        // if (getDisplayPreferences(ANNOTATIONS)) {
        // MediaSeries<ImageElement> series = view2DPane.getSeries();
        //
        // Boolean synchLink = (Boolean) view2DPane.getActionValue(ActionW.SYNCH_LINK.cmd());
        // String str = synchLink != null && synchLink ? "linked" : "unlinked";
        // paintFontOutline(g2, str, bound.width - g2.getFontMetrics().stringWidth(str) - border, drawY);
        //
        // }

    }

    public Rectangle2D getOutLine(Line2D l) {
        Rectangle2D r = l.getBounds2D();
        r.setFrame(r.getX() - 1.0, r.getY() - 1.0, r.getWidth() + 2.0, r.getHeight() + 2.0);
        return r;
    }

    public void drawLUT(Graphics2D g2, Rectangle bound, float midfontHeight) {
        ByteLut lut = (ByteLut) view2DPane.getActionValue(ActionW.LUT.cmd());
        if (lut != null && bound.height > 350) {

            if (lut.getLutTable() == null) {
                lut = ByteLut.grayLUT;
            }
            byte[][] table =
                (Boolean) view2DPane.getActionValue(ActionW.INVERSELUT.cmd()) ? lut.getInvertedLutTable() : lut
                    .getLutTable();
            float length = table[0].length;
            float x = bound.width - 30f;
            float y = bound.height / 2f - length / 2f;

            g2.setPaint(Color.black);
            Rectangle2D.Float rect = new Rectangle2D.Float(x - 11f, y - 2f, 12f, 2f);
            g2.draw(rect);
            int separation = 4;
            float step = length / separation;
            for (int i = 1; i < separation; i++) {
                float posY = y + i * step;
                rect.setRect(x - 6f, posY - 1f, 7f, 2f);
                g2.draw(rect);
            }
            rect.setRect(x - 11f, y + length, 12f, 2f);
            g2.draw(rect);
            rect.setRect(x - 2f, y - 2f, 23f, length + 4f);
            g2.draw(rect);

            g2.setPaint(Color.white);
            Line2D.Float line = new Line2D.Float(x - 10f, y - 1f, x - 1f, y - 1f);
            g2.draw(line);
            float stepWindow = (Float) view2DPane.getActionValue(ActionW.WINDOW.cmd()) / separation;
            float firstlevel = (Float) view2DPane.getActionValue(ActionW.LEVEL.cmd()) - stepWindow * 2f;
            String str = "" + (int) firstlevel; //$NON-NLS-1$
            GraphicLabel.paintFontOutline(g2, str, x - g2.getFontMetrics().stringWidth(str) - 12f, y + midfontHeight);
            for (int i = 1; i < separation; i++) {
                float posY = y + i * step;
                line.setLine(x - 5f, posY, x - 1f, posY);
                g2.draw(line);
                str = "" + (int) (firstlevel + i * stepWindow); //$NON-NLS-1$
                GraphicLabel.paintFontOutline(g2, str, x - g2.getFontMetrics().stringWidth(str) - 7, posY
                    + midfontHeight);
            }

            line.setLine(x - 10f, y + length + 1f, x - 1f, y + length + 1f);
            g2.draw(line);
            str = "" + (int) (firstlevel + 4 * stepWindow); //$NON-NLS-1$
            GraphicLabel.paintFontOutline(g2, str, x - g2.getFontMetrics().stringWidth(str) - 12, y + length
                + midfontHeight);
            rect.setRect(x - 1f, y - 1f, 21f, length + 2f);
            g2.draw(rect);

            for (int k = 0; k < length; k++) {
                g2.setPaint(new Color(table[0][k] & 0xff, table[1][k] & 0xff, table[2][k] & 0xff));
                rect.setRect(x, y + k, 19f, 1f);
                g2.draw(rect);
            }
        }
    }

    public void drawScale(Graphics2D g2d, Rectangle bound, float fontHeight) {
        ImageElement image = view2DPane.getImage();
        RenderedImage source = view2DPane.getSourceImage();
        if (source == null) {
            return;
        }

        double zoomFactor = view2DPane.getViewModel().getViewScale();

        double scale = image.getPixelSize() / zoomFactor;
        double scaleSizex =
            ajustShowScale(scale,
                (int) Math.min(zoomFactor * source.getWidth() * image.getRescaleX(), bound.width / 2.0));
        if (showBottomScale & scaleSizex > 30.0d) {
            Unit[] unit = { image.getPixelSpacingUnit() };
            String str = ajustLengthDisplay(scaleSizex * scale, unit);
            g2d.setPaint(color);
            g2d.setStroke(new BasicStroke(1.0F));
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setPaint(Color.black);

            double posx = bound.width / 2.0 - scaleSizex / 2.0;
            double posy = bound.height - border - 1.5; // -1.5 for outline
            Line2D line = new Line2D.Double(posx, posy, posx + scaleSizex, posy);
            g2d.draw(getOutLine(line));
            line.setLine(posx, posy - 15.0, posx, posy);
            g2d.draw(getOutLine(line));
            line.setLine(posx + scaleSizex, posy - 15.0, posx + scaleSizex, posy);
            g2d.draw(getOutLine(line));
            int divisor = str.indexOf("5") == -1 ? str.indexOf("2") == -1 ? 10 : 2 : 5; //$NON-NLS-1$ //$NON-NLS-2$
            double divSquare = scaleSizex / divisor;
            for (int i = 1; i < divisor; i++) {
                line.setLine(posx + divSquare * i, posy, posx + divSquare * i, posy - 10.0);
                g2d.draw(getOutLine(line));
            }
            if (divSquare > 90) {
                double secondSquare = divSquare / 10.0;
                for (int i = 0; i < divisor; i++) {
                    for (int k = 1; k < 10; k++) {
                        double secBar = posx + divSquare * i + secondSquare * k;
                        line.setLine(secBar, posy, secBar, posy - 5.0);
                        g2d.draw(getOutLine(line));
                    }
                }
            }

            g2d.setPaint(Color.white);
            line.setLine(posx, posy, posx + scaleSizex, posy);
            g2d.draw(line);
            line.setLine(posx, posy - 15.0, posx, posy);
            g2d.draw(line);
            line.setLine(posx + scaleSizex, posy - 15.0, posx + scaleSizex, posy);
            g2d.draw(line);
            for (int i = 0; i < divisor; i++) {
                line.setLine(posx + divSquare * i, posy, posx + divSquare * i, posy - 10.0);
                g2d.draw(line);
            }
            if (divSquare > 90) {
                double secondSquare = divSquare / 10.0;
                for (int i = 0; i < divisor; i++) {
                    for (int k = 1; k < 10; k++) {
                        double secBar = posx + divSquare * i + secondSquare * k;
                        line.setLine(secBar, posy, secBar, posy - 5.0);
                        g2d.draw(line);
                    }
                }
            }
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
            String pixSizeDesc = image.getPixelSizeCalibrationDescription();
            if (pixSizeDesc != null) {
                GraphicLabel.paintFontOutline(g2d, pixSizeDesc, (float) (posx + scaleSizex + 5), (float) posy
                    - fontHeight);
            }
            str += " " + unit[0].getAbbreviation(); //$NON-NLS-1$
            GraphicLabel.paintFontOutline(g2d, str, (float) (posx + scaleSizex + 5), (float) posy);
        }

        double scaleSizeY =
            ajustShowScale(scale,
                (int) Math.min(zoomFactor * source.getHeight() * image.getRescaleY(), bound.height / 2.0));

        if (scaleSizeY > 30.0d) {
            Unit[] unit = { image.getPixelSpacingUnit() };
            String str = ajustLengthDisplay(scaleSizeY * scale, unit);

            g2d.setPaint(color);
            g2d.setStroke(new BasicStroke(1.0F));
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setPaint(Color.black);

            double posx = border + 20;
            double posy = bound.height / 2.0 - scaleSizeY / 2.0;
            Line2D line = new Line2D.Double(posx, posy, posx, posy + scaleSizeY);
            g2d.draw(getOutLine(line));
            line.setLine(posx, posy, posx + 15, posy);
            g2d.draw(getOutLine(line));
            line.setLine(posx, posy + scaleSizeY, posx + 15, posy + scaleSizeY);
            g2d.draw(getOutLine(line));
            int divisor = str.indexOf("5") == -1 ? str.indexOf("2") == -1 ? 10 : 2 : 5; //$NON-NLS-1$ //$NON-NLS-2$
            double divSquare = scaleSizeY / divisor;
            for (int i = 0; i < divisor; i++) {
                line.setLine(posx, posy + divSquare * i, posx + 10.0, posy + divSquare * i);
                g2d.draw(getOutLine(line));
            }
            if (divSquare > 90) {
                double secondSquare = divSquare / 10.0;
                for (int i = 0; i < divisor; i++) {
                    for (int k = 1; k < 10; k++) {
                        double secBar = posy + divSquare * i + secondSquare * k;
                        line.setLine(posx, secBar, posx + 5.0, secBar);
                        g2d.draw(getOutLine(line));
                    }
                }
            }

            g2d.setPaint(Color.white);
            line.setLine(posx, posy, posx, posy + scaleSizeY);
            g2d.draw(line);
            line.setLine(posx, posy, posx + 15, posy);
            g2d.draw(line);
            line.setLine(posx, posy + scaleSizeY, posx + 15, posy + scaleSizeY);
            g2d.draw(line);
            for (int i = 0; i < divisor; i++) {
                line.setLine(posx, posy + divSquare * i, posx + 10.0, posy + divSquare * i);
                g2d.draw(line);
            }
            if (divSquare > 90) {
                double secondSquare = divSquare / 10.0;
                for (int i = 0; i < divisor; i++) {
                    for (int k = 1; k < 10; k++) {
                        double secBar = posy + divSquare * i + secondSquare * k;
                        line.setLine(posx, secBar, posx + 5.0, secBar);
                        g2d.draw(line);
                    }
                }
            }

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);

            GraphicLabel.paintFontOutline(g2d, str + " " + unit[0].getAbbreviation(), (int) posx, (int) (posy - 5)); //$NON-NLS-1$
        }

    }

    private double ajustShowScale(double ratio, int maxLength) {
        int digits = (int) ((Math.log(maxLength * ratio) / Math.log(10)) + 1);
        double scaleLength = Math.pow(10, digits);
        double scaleSize = scaleLength / ratio;

        int loop = 0;
        while ((int) scaleSize > maxLength) {
            scaleLength /= findGeometricSuite(scaleLength);
            scaleSize = scaleLength / ratio;
            loop++;
            if (loop > 50) {
                return 0.0;
            }
        }
        return scaleSize;
    }

    public double findGeometricSuite(double length) {
        int shift = (int) ((Math.log(length) / Math.log(10)) + 0.1);
        int firstDigit = (int) (length / Math.pow(10, shift) + 0.5);
        if (firstDigit == 5) {
            return 2.5;
        }
        return 2.0;

    }

    public String ajustLengthDisplay(double scaleLength, Unit[] unit) {
        double ajustScaleLength = scaleLength;

        Unit ajustUnit = unit[0];

        if (scaleLength < 1.0) {
            Unit down = ajustUnit;
            while ((down = down.getDownUnit()) != null) {
                double length = scaleLength * down.getConversionRatio(unit[0].getConvFactor());
                if (length > 1) {
                    ajustUnit = down;
                    ajustScaleLength = length;
                    break;
                }
            }
        } else if (scaleLength > 10.0) {
            Unit up = ajustUnit;
            while ((up = up.getUpUnit()) != null) {
                double length = scaleLength * up.getConversionRatio(unit[0].getConvFactor());
                if (length < 1) {
                    break;
                }
                ajustUnit = up;
                ajustScaleLength = length;
            }
        }
        // Trick to keep the value as a return parameter
        unit[0] = ajustUnit;
        if (ajustScaleLength < 1.0) {
            return ajustScaleLength < 0.001 ? DecFormater.scientificFormat(ajustScaleLength) : DecFormater
                .fourDecimal(ajustScaleLength);
        }
        return ajustScaleLength > 50000.0 ? DecFormater.scientificFormat(ajustScaleLength) : DecFormater
            .twoDecimal(ajustScaleLength);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.weasis.dicom.viewer2d.AnnotationsLayer#getDisplayPreferences(java.lang.String)
     */
    @Override
    public boolean getDisplayPreferences(String item) {
        Boolean val = displayPreferences.get(item);
        return val == null ? false : val;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public int getLevel() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setLevel(int i) {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.weasis.dicom.viewer2d.AnnotationsLayer#setDisplayPreferencesValue(java.lang.String, boolean)
     */
    @Override
    public boolean setDisplayPreferencesValue(String displayItem, boolean selected) {
        boolean selected2 = getDisplayPreferences(displayItem);
        displayPreferences.put(displayItem, selected);
        return selected != selected2;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.weasis.dicom.viewer2d.AnnotationsLayer#getPreloadingProgressBound()
     */
    @Override
    public Rectangle getPreloadingProgressBound() {
        return preloadingProgressBound;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.weasis.dicom.viewer2d.AnnotationsLayer#getPixelInfoBound()
     */
    @Override
    public Rectangle getPixelInfoBound() {
        return pixelInfoBound;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.weasis.dicom.viewer2d.AnnotationsLayer#setPixelInfo(java.lang.String)
     */
    @Override
    public void setPixelInfo(String pixelInfo) {
        this.pixelInfo = pixelInfo;
    }

    @Override
    public AnnotationsLayer getLayerCopy(DefaultView2d view2dPane) {
        InfoLayer layer = new InfoLayer(view2DPane);
        HashMap<String, Boolean> prefs = layer.displayPreferences;
        prefs.put(ANNOTATIONS, getDisplayPreferences(ANNOTATIONS));
        prefs.put(SCALE, getDisplayPreferences(SCALE));
        prefs.put(LUT, getDisplayPreferences(LUT));
        prefs.put(PIXEL, getDisplayPreferences(PIXEL));
        prefs.put(WINDOW_LEVEL, getDisplayPreferences(WINDOW_LEVEL));
        prefs.put(ZOOM, getDisplayPreferences(ZOOM));
        prefs.put(ROTATION, getDisplayPreferences(ROTATION));
        return layer;
    }

    @Override
    public int getBorder() {
        return border;
    }

    @Override
    public void setBorder(int border) {
        this.border = border;
    }
}
