/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included with this distribution in  *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.batik.gvt.text;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.awt.font.GlyphVector;
import java.awt.font.GlyphMetrics;
import java.awt.font.LineMetrics;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.text.AttributedCharacterIterator;
import java.text.CharacterIterator;
import java.text.AttributedString;

import org.apache.batik.gvt.text.TextSpanLayout;
import org.apache.batik.gvt.text.GVTAttributedCharacterIterator;
import org.apache.batik.gvt.text.TextHit;

/**
 * Implementation of TextSpanLayout which uses java.awt.font.GlyphVector.
 * @see org.apache.batik.gvt.TextSpanLayout.
 *
 * @author <a href="bill.haneman@ireland.sun.com>Bill Haneman</a>
 * @version $Id$
 */
public class GlyphLayout implements TextSpanLayout {

    private GlyphVector gv;
    private Font font;
    private LineMetrics metrics;
    private AttributedCharacterIterator aci;
    private CharacterIterator ci;
    private FontRenderContext frc;
    private AffineTransform transform;
    private Point2D advance;
    private Point2D offset;
    private Point2D prevCharPosition;
    protected Shape[] glyphLogicalBounds;
    protected Point2D[] glyphPositions;

    /**
     * Creates the specified text layout using the
     * specified AttributedCharacterIterator and rendering context.
     * @param aci the AttributedCharacterIterator whose text is to
     *  be laid out
     * @param frc the FontRenderContext to use for generating glyphs.
     */
    public GlyphLayout(AttributedCharacterIterator aci, Point2D offset,
                          FontRenderContext frc) {
        this.aci = (AttributedCharacterIterator) aci.clone();
        this.frc = frc;
        this.font = getFont(aci);
        this.metrics = font.getLineMetrics(
                           this.aci, aci.getBeginIndex(), aci.getEndIndex(), frc);

        ci = new ReorderedCharacterIterator(this.aci);
        this.gv = font.createGlyphVector(frc, ci);
        this.gv.performDefaultLayout();
        this.glyphPositions = new Point2D.Float[gv.getNumGlyphs()];
        this.offset = offset;
        this.transform = null;
        doExplicitGlyphLayout(false);
        adjustTextSpacing();
        computeGlyphLogicalBounds();
    }

    /**
     * Paints the specified text layout using the
     * specified Graphics2D and rendering context.
     * @param g2d the Graphics2D to use
     * @param x the x position of the rendered layout origin.
     * @param y the y position of the rendered layout origin.
     */
    public void draw(Graphics2D g2d) {
        AffineTransform t;
        if (transform != null) {
            t = g2d.getTransform();
            g2d.transform(transform);
            g2d.drawGlyphVector(gv, 0f, 0f);
            g2d.setTransform(t);
        } else {
            g2d.drawGlyphVector(gv, 0f, 0f);
        }
    }

    /**
     * Returns the outline of the completed glyph layout, transformed
     * by an AffineTransform.
     * @param t an AffineTransform to apply to the outline before returning it.
     */
    public Shape getOutline() {
        Shape s = gv.getOutline(0f, 0f);
        if (transform != null) {
            s = transform.createTransformedShape(s);
        }
        return s;
    }

    /**
     * Returns the current text position at the beginning
     * of glyph layout, before the application of explicit
     * glyph positioning attributes.
     */
    public Point2D getOffset() {
        return offset;
    }

    /**
     * Sets the text position used for the implicit origin
     * of glyph layout. Ignored if multiple explicit glyph
     * positioning attributes are present in ACI
     * (e.g. if the aci has multiple X or Y values).
     */
    public void setOffset(Point2D offset) {
        //System.out.println("Offset set to "+offset);
        this.offset = offset;
        this.gv.performDefaultLayout();
        doExplicitGlyphLayout(true);
        adjustTextSpacing();
        computeGlyphLogicalBounds();
    }

    /**
     * Returns the outline of the specified decorations on the glyphs,
     * @param decorationType an integer indicating the type(s) of decorations
     *     included in this shape.  May be the result of "OR-ing" several
     *     values together:
     * e.g. <tt>DECORATION_UNDERLINE | DECORATION_STRIKETHROUGH</tt>
     */
    public Shape getDecorationOutline(int decorationType) {
        Shape g = new GeneralPath();
        if ((decorationType & DECORATION_UNDERLINE) != 0) {
             ((GeneralPath) g).append(getUnderlineShape(), false);
        }
        if ((decorationType & DECORATION_STRIKETHROUGH) != 0) {
             ((GeneralPath) g).append(getStrikethroughShape(), false);
        }
        if ((decorationType & DECORATION_OVERLINE) != 0) {
             ((GeneralPath) g).append(getOverlineShape(), false);
        }
        if (transform != null) {
            g = transform.createTransformedShape(g);
        }
        return g;
    }

    /**
     * Returns the rectangular bounds of the completed glyph layout.
     */
    public Rectangle2D getBounds() {
        Rectangle2D bounds = gv.getVisualBounds();
        if (transform != null) {
            bounds = transform.createTransformedShape(bounds).getBounds2D();
        }
        return bounds;
    }

    /**
     * Returns the rectangular bounds of the completed glyph layout,
     * inclusive of "decoration" (underline, overline, etc.)
     */
    public Rectangle2D getDecoratedBounds() {
        return getBounds().createUnion(
            getDecorationOutline(
                DECORATION_ALL).getBounds2D());
    }

    /**
     * Returns the dimension of the completed glyph layout in the
     * primary text advance direction (e.g. width, for RTL or LTR text).
     * (This is the dimension that should be used for positioning
     * adjacent layouts.)
     */
    public float getAdvance() {
        return (float) advance.getX();
    }

    /**
     * Returns the current text position at the completion
     * of glyph layout.
     * (This is the position that should be used for positioning
     * adjacent layouts.)
     */
    public Point2D getAdvance2D() {
        return advance;
    }

    /**
     * Returns a Shape which encloses the currently selected glyphs
     * as specified by glyph indices <tt>begin/tt> and <tt>end</tt>.
     * @param begin the index of the first glyph in the contiguous selection.
     * @param end the index of the last glyph in the contiguous selection.
     */
    public Shape getLogicalHighlightShape(int begin, int end) {

        Shape shape = null;
        begin = Math.max(0, begin);
        end = Math.min(end, gv.getNumGlyphs());

        if (begin == 0 && end == gv.getNumGlyphs()) {
           shape = new GeneralPath(getBounds());
        } else {
            for (int i=begin; i<end; ++i) {

                Shape gbounds = getGlyphLogicalBounds(i);
                Rectangle2D gbounds2d = gbounds.getBounds2D();

                if (shape == null) {
                   shape = new GeneralPath(gbounds2d);
                } else {
                   ((GeneralPath) shape).append(gbounds2d, false);
                }

            }
            if (transform != null) {
                shape = transform.createTransformedShape(shape);
            }
        }
        return shape;
    }

    /**
     * Perform hit testing for coordinate at x, y.
     * @return a TextHit object encapsulating the character index for
     *     successful hits and whether the hit is on the character
     *     leading edge.
     * @param x the x coordinate of the point to be tested.
     * @param y the y coordinate of the point to be tested.
     */
    public TextHit hitTestChar(float x, float y) {
        int begin = 0;
        int end = gv.getNumGlyphs();
        TextHit textHit;
        GlyphMetrics gm;
        float maxX = (float) gv.getVisualBounds().getX();
        if (transform != null) {
            try {
                Point2D p = new Point2D.Float(x, y);
                transform.inverseTransform(p, p);
                x = (float) p.getX();
                y = (float) p.getY();
            } catch (java.awt.geom.NoninvertibleTransformException nite) {;}
        }
        for (int i=begin; i<end; ++i) {
            Shape gbounds = getGlyphLogicalBounds(i);

            Rectangle2D gbounds2d = gbounds.getBounds2D();

            if (gbounds2d.getX()+gbounds2d.getWidth() > maxX) {
                maxX = (float) (gbounds2d.getX()+gbounds2d.getWidth());
            }

            if (gbounds.contains(x, y)) {
                boolean isRightHalf =
                    (x > (gbounds2d.getX()+(gbounds2d.getWidth()/2d)));
                boolean isLeadingEdge = !isRightHalf;
                textHit = new TextHit(i, isLeadingEdge);
                return textHit;
            }
        }

        // fallthrough: in text bbox but not on a glyph
        textHit = new TextHit(-1, false);

        return textHit;
    }

    /**
     * Returns true if the advance direction of this text is vertical.
     */
    public boolean isVertical() {
        // TODO: Implement this!
        return false;
    }

    /**
     * Returns the number of characters in this layout.
     */
    public int getCharacterCount() {
        return gv.getNumGlyphs();
        // XXX: probably wrong for CTL, work to be done here!
    }


    protected Shape getGlyphLogicalBounds(int i) {

        // We can't use GlyphVector.getGlyphLogicalBounds(i)
        // since it seems to have a nasty bug!

        return glyphLogicalBounds[i];
    }


    // private

    private void computeGlyphLogicalBounds() {

        int c = gv.getNumGlyphs();

        glyphLogicalBounds = new Rectangle2D.Double[c];

        Rectangle2D.Double lbox = null;

        for (int i=0; i<c; ++i) {

            GlyphMetrics gm = gv.getGlyphMetrics(i);
            Rectangle2D gbounds2d = gm.getBounds2D();
            Point2D gpos = glyphPositions[i];
            lbox = new Rectangle2D.Double(
                                    gpos.getX()+gbounds2d.getX(),
                                    gpos.getY()+gbounds2d.getY(),
                                    gbounds2d.getWidth(),
                                    gbounds2d.getHeight());

            glyphLogicalBounds[i] = lbox;
        }

        for (int i=0; i<c; ++i) {

            int begin = i;
            int end = begin;
            Point2D gpos = glyphPositions[begin];

            // calculate a "run" over the same y nominal position,
            // over which the glyphs have positive 'x' advances.
            // (means that RTL "runs" are not yet supported, sorry)

            float y = (float) gpos.getY();
            float x = (float) gpos.getX();
            lbox = (Rectangle2D.Double) glyphLogicalBounds[begin];
            float miny = (float) lbox.getY();
            float maxy = (float) (lbox.getY() + lbox.getHeight());
            float epsilon = (float) lbox.getHeight()/8f;
            float currY = y;
            float currX = x;
            while (end<c) {
                lbox = (Rectangle2D.Double) glyphLogicalBounds[end];
                currY = (float) glyphPositions[end].getY();
                currX = (float) glyphPositions[end].getX();
                if ((currX < x) || (Math.abs(currY - y) > epsilon)) {
                    break;
                }
                miny =
                  Math.min((float) lbox.getY(), miny);
                float h = (float) (lbox.getY() + lbox.getHeight());
                maxy =
                  Math.max(h, maxy);
                ++end;
            }
            i = (end > begin) ? end-1 : end;

            Rectangle2D.Double lboxPrev = null;

            float prevx = 0f;

            for (int n=begin; n<end; ++n) {

                // extend the vertical bbox for this run
                lbox = (Rectangle2D.Double) glyphLogicalBounds[n];

                x = (float) lbox.getX();

                // adjust left bounds if not first in run
                if (lboxPrev != null) {
                    x = (float) (x + (prevx+lboxPrev.getWidth()))/2f;
                    glyphLogicalBounds[n-1] =
                         new Rectangle2D.Double(
                              (double) prevx,
                              lboxPrev.getY(),
                              (double) (x - prevx),
                              lboxPrev.getHeight());

                    //System.out.println("Setting glb["+(n-1)+"]="+
                    //                       glyphLogicalBounds[n-1]);
                }

                lbox =
                     new Rectangle2D.Double(
                          (double) x,
                          (double) miny,
                          Math.max(0d, lbox.getWidth()+(lbox.getX() - x)),
                          (double) (maxy - miny));

                lboxPrev = lbox;
                prevx = x;
                glyphLogicalBounds[n] = lbox;
            }
        }
    }

//inner classes

    protected class ReorderedCharacterIterator implements CharacterIterator {

        private int ndx;
        private int begin;
        private int end;
        private char[] c;

        ReorderedCharacterIterator(AttributedCharacterIterator aci) {

            int aciIndex = aci.getBeginIndex();
            begin = 0;
            end = aci.getEndIndex()-aciIndex;
            ndx = begin;
            c = new char[end-begin];

            // set increment and initial array index according to
            // run direction of this aci

            int inc = (aci.getAttribute(TextAttribute.RUN_DIRECTION) ==
                                    TextAttribute.RUN_DIRECTION_RTL) ? -1 : 1;

            ndx = (inc > 0) ? begin : end-1;

            // reordering section
            char ch = aci.first();
            while (ch != CharacterIterator.DONE) {

                 // get BiDi embedding
                 Integer embed = (Integer) aci.getAttribute(
                                               TextAttribute.BIDI_EMBEDDING);
                 //System.out.println("BiDi embedding level : "+embed);
                 // get BiDi span
                 int runLimit = aci.getRunLimit(TextAttribute.BIDI_EMBEDDING);

                 boolean isReversed = false;
                 int runEndNdx = ndx;

                 if (embed != null) {
                     isReversed = (Math.abs(Math.IEEEremainder(
                         (double)embed.intValue(), 2d)) < 0.1) ? false : true;
                     if (isReversed) {
                         runEndNdx = ndx + inc*(runLimit-aciIndex);
                         inc = -inc;
                         ndx = runEndNdx + inc;
                     }
                 }

                 for (;aciIndex < runLimit; ch=aci.next(),++aciIndex) {
                      c[ndx] = ch;
                      //System.out.println("Setting c["+ndx+"] to "+ch);
                      ndx += inc;
                 }
                 if (isReversed) { // undo the reversal, run is done
                      ndx = runEndNdx;
                      inc = -inc;
                 }
            }

            aci.first();
            ndx = begin;
        }

        public Object clone() {
            return new ReorderedCharacterIterator(
                           (AttributedCharacterIterator) aci.clone());
        }

        public char current() {
            return c[ndx];
        }

        public char first() {
            ndx=begin;
            return c[ndx];
        }

        public int getBeginIndex() {
            return begin;
        }

        public int getEndIndex() {
            return end;
        }

        public int getIndex() {
            return ndx;
        }

        public char last() {
            ndx = end-1;
            return c[end-1];
        }

        public char next() {
            ++ndx;
            if (ndx >= end) {
                ndx = end;
                return CharacterIterator.DONE;
            }
            else return c[ndx];
        }

        public char previous() {
            --ndx;
            if (ndx < begin) {
                ndx = begin;
                return c[ndx];
            }
            else return c[ndx];
        }

        public char setIndex(int position) {
            if (position < begin || position > end) {
                throw new IllegalArgumentException();
            }
            ndx = position;
            return c[ndx];
        }
    }

//protected

    /**
     * Returns a shape describing the overline decoration for a given ACI.
     */
    protected Shape getOverlineShape() {
        double y = metrics.getBaselineOffsets()[java.awt.Font.ROMAN_BASELINE] -
                metrics.getAscent();
        Stroke overlineStroke =
            new BasicStroke(metrics.getUnderlineThickness());
        return overlineStroke.createStrokedShape(
                           new java.awt.geom.Line2D.Double(
                           0f, y,
                           getAdvance(), y));
    }

    /**
     * Returns a shape describing the strikethrough line for a given ACI.
     */
    protected Shape getUnderlineShape() {
        double y = metrics.getUnderlineOffset();
        Stroke underlineStroke =
            new BasicStroke(metrics.getUnderlineThickness());

        return underlineStroke.createStrokedShape(
                           new java.awt.geom.Line2D.Double(
                           0f, y,
                           getAdvance(), y));
    }

    /**
     * Returns a shape describing the strikethrough line for a given ACI.
     */
    protected Shape getStrikethroughShape() {
        double y = metrics.getStrikethroughOffset();
        Stroke strikethroughStroke =
            new BasicStroke(metrics.getStrikethroughThickness());

        return strikethroughStroke.createStrokedShape(
                           new java.awt.geom.Line2D.Double(
                           0f, y,
                           getAdvance(), y));
    }

    protected Font getFont(AttributedCharacterIterator aci) {
        aci.first();
        return new Font(aci.getAttributes());
    }

    protected void adjustTextSpacing() {

        aci.first();
        Boolean customSpacing =  (Boolean) aci.getAttribute(
               GVTAttributedCharacterIterator.TextAttribute.CUSTOM_SPACING);
        Float length = (Float) aci.getAttribute(
               GVTAttributedCharacterIterator.TextAttribute.BBOX_WIDTH);
        Integer lengthAdjust = (Integer) aci.getAttribute(
              GVTAttributedCharacterIterator.TextAttribute.LENGTH_ADJUST);
        if ((customSpacing != null) && customSpacing.booleanValue()) {
            applySpacingParams(length, lengthAdjust,
               (Float) aci.getAttribute(
               GVTAttributedCharacterIterator.TextAttribute.KERNING),
               (Float) aci.getAttribute(
               GVTAttributedCharacterIterator.TextAttribute.LETTER_SPACING),
               (Float) aci.getAttribute(
               GVTAttributedCharacterIterator.TextAttribute.WORD_SPACING));
        }

        if (lengthAdjust ==
            GVTAttributedCharacterIterator.TextAttribute.ADJUST_ALL) {
               transform = computeStretchTransform(length);
        }
    }

    protected AffineTransform computeStretchTransform(Float length) {
        AffineTransform t = null;
        if (length!= null && !length.isNaN()) {
            double xscale = 1d;
            double yscale = 1d;
            if (isVertical()) {
                yscale = length.floatValue()/gv.getVisualBounds().getHeight();
            } else {
                xscale = length.floatValue()/gv.getVisualBounds().getWidth();
            }
            try {
                Point2D startPos = gv.getGlyphPosition(0);
                AffineTransform translation =
                        AffineTransform.getTranslateInstance(
                                              startPos.getX(),
                                              startPos.getY());
                AffineTransform inverse = translation.createInverse();
                t = translation;
                t.concatenate(
                        AffineTransform.getScaleInstance(xscale, yscale));
                t.concatenate(inverse);
            } catch (java.awt.geom.NoninvertibleTransformException e) {;}
        }
        return t;
    }

    protected void applySpacingParams(Float length,
                                      Integer lengthAdjust,
                                      Float kern,
                                      Float letterSpacing,
                                      Float wordSpacing) {

       /**
        * Two passes required when textLength is specified:
        * First, apply spacing properties,
        * then adjust spacing with new advances based on ratio
        * of expected length to actual advance.
        */

        advance = doSpacing(kern, letterSpacing, wordSpacing);
        if ((lengthAdjust ==
             GVTAttributedCharacterIterator.TextAttribute.ADJUST_SPACING) &&
                 length!= null && !length.isNaN()) { // adjust if necessary
            float xscale = 1f;
            float yscale = 1f;
            if (!isVertical()) {
                xscale = length.floatValue()/(float) gv.getVisualBounds().getWidth();
            } else {
                yscale = length.floatValue()/(float) gv.getVisualBounds().getHeight();
            }
            rescaleSpacing(xscale, yscale);
        }
    }

    protected Point2D doSpacing(Float kern,
                             Float letterSpacing,
                             Float wordSpacing) {

        boolean autoKern = true;
        boolean doWordSpacing = false;
        boolean doLetterSpacing = false;
        float kernVal = 0f;
        float letterSpacingVal = 0f;
        float wordSpacingVal = 0f;

        if ((kern instanceof Float) && (!kern.isNaN())) {
            kernVal = kern.floatValue();
            autoKern = false;
            //System.out.println("KERNING: "+kernVal);
        }
        if ((letterSpacing instanceof Float) && (!letterSpacing.isNaN())) {
            letterSpacingVal = letterSpacing.floatValue();
            doLetterSpacing = true;
            //System.out.println("LETTER-SPACING: "+letterSpacingVal);
        }
        if ((wordSpacing instanceof Float) && (!wordSpacing.isNaN())) {
            wordSpacingVal = wordSpacing.floatValue();
            doWordSpacing = true;
            //System.out.println("WORD_SPACING: "+wordSpacingVal);
        }

        int numGlyphs = gv.getNumGlyphs();

        float dx = 0f;
        float dy = 0f;
        Point2D newPositions[] = new Point2D[numGlyphs];
        Point2D prevPos = glyphPositions[0];
        float x = (float) prevPos.getX();
        float y = (float) prevPos.getY();
        try {
        if ((numGlyphs > 1) &&
                            (doWordSpacing || doLetterSpacing || !autoKern)) {
            for (int i=1; i<numGlyphs; ++i) {
                Point2D gpos = glyphPositions[i];
                dx = (float)gpos.getX()-(float)prevPos.getX();
                dy = (float)gpos.getY()-(float)prevPos.getY();
                boolean inWS = false;
                // while this is whitespace, increment
                int beginWS = i;
                int endWS = i;
                GlyphMetrics gm = gv.getGlyphMetrics(i);
                // BUG: gm.isWhitespace() fails for latin SPACE glyph!
                while ((gm.getBounds2D().getWidth()<0.01d) ||
                                                       gm.isWhitespace()) {
                    ++i;
                    ++endWS;
                    if (!inWS) inWS = true;
                    if (i>=numGlyphs) {
                        inWS = false;
                        break;
                    }
                    gpos = glyphPositions[i];
                    gm = gv.getGlyphMetrics(i);
                }
                // then apply wordSpacing
                if ( inWS ) {
                    if (doWordSpacing) {
                        int nWS = endWS-beginWS;
                        float px = (float) prevPos.getX();
                        float py = (float) prevPos.getY();
                        dx = (float) (gpos.getX() - px)/(nWS+1);
                        dy = (float) (gpos.getY() - py)/(nWS+1);
                        if (isVertical()) {
                            dy += (float) wordSpacing.floatValue()/(nWS+1);
                        } else {
                            dx += (float) wordSpacing.floatValue()/(nWS+1);
                        }
                        for (int j=beginWS; j<=endWS; ++j) {
                            x += dx;
                            y += dy;
                            newPositions[j] = new Point2D.Float(x, y);
                        }
                    }
                } else {
                    dx = (float) (gpos.getX()-prevPos.getX());
                    dy = (float) (gpos.getY()-prevPos.getY());
                    if (autoKern) {
                        if (isVertical()) dy += letterSpacingVal;
                        else dx += letterSpacingVal;
                    } else {
                        // apply explicit kerning adjustments,
                        // discarding any auto-kern dx values
                        if (isVertical()) {
                            dy = (float)
                              gv.getGlyphMetrics(i-1).getBounds2D().getWidth()+
                              kernVal + letterSpacingVal;
                        } else {
                            dx = (float)
                              gv.getGlyphMetrics(i-1).getBounds2D().getWidth()+
                              kernVal + letterSpacingVal;
                        }
                    }
                    x += dx;
                    y += dy;
                    newPositions[i] = new Point2D.Float(x, y);
                }
                prevPos = gpos;
            }
            for (int i=1; i<numGlyphs; ++i) { // assign the new positions
                glyphPositions[i] = newPositions[i];
                gv.setGlyphPosition(i, glyphPositions[i]);
            }
        }
        if (isVertical()) {
            dx = 0f;
            dy = (float)
                gv.getGlyphMetrics(numGlyphs-1).getBounds2D().getHeight()+
                                              kernVal+letterSpacingVal;
        } else {
            dx = (float)
                gv.getGlyphMetrics(numGlyphs-1).getBounds2D().getWidth()+
                                              kernVal+letterSpacingVal;
            dy = 0f;
        }
        } catch (Exception e) {
            e.printStackTrace();
        }
        //Point2D newAdvance = new Point2D.Float((float) prevPos.getX()+dx,
        //                                       (float) prevPos.getY()+dy);
        Point2D newAdvance = advance;
        gv.setGlyphPosition(numGlyphs, newAdvance);
        return newAdvance;
    }

    protected void rescaleSpacing(float xscale, float yscale) {
        Rectangle2D bounds = gv.getVisualBounds();
        float initX = (float) bounds.getX();
        float initY = (float) bounds.getY();
        int numGlyphs = gv.getNumGlyphs();
        float dx = 0f;
        float dy = 0f;
        for (int i=0; i<numGlyphs; ++i) {
            Point2D gpos = glyphPositions[i];
            dx = (float)gpos.getX()-initX;
            dy = (float)gpos.getY()-initY;
            glyphPositions[i] = new Point2D.Float(initX+dx*xscale,
                                                  initY+dy*yscale);
            gv.setGlyphPosition(i, glyphPositions[i]);
        }
        gv.setGlyphPosition(numGlyphs, new Point2D.Float(initX+dx*xscale,
                                                             initY+dy*yscale));
        advance = new Point2D.Float((float)(initX+dx*xscale-offset.getX()),
                                       (float)(initY+dy*yscale-offset.getY()));
    }


    protected void doExplicitGlyphLayout(boolean applyOffset) {
        char ch = aci.first();
        int i=0;
        float[] gp = new float[(gv.getNumGlyphs()+1)*2];
        gp = (float[]) gv.getGlyphPositions(0, gv.getNumGlyphs(), gp).clone();
        float init_x_pos = (float) offset.getX();
        float init_y_pos = (float) offset.getY();
        float curr_x_pos = gp[0] + init_x_pos;
        float curr_y_pos = gp[1] + init_y_pos;

        while ((ch != CharacterIterator.DONE) && (i < gp.length/2)) {
            Float x = (Float) aci.getAttribute(
                             GVTAttributedCharacterIterator.TextAttribute.X);
            Float dx = (Float) aci.getAttribute(
                             GVTAttributedCharacterIterator.TextAttribute.DX);
            Float y = (Float) aci.getAttribute(
                             GVTAttributedCharacterIterator.TextAttribute.Y);
            Float dy = (Float) aci.getAttribute(
                             GVTAttributedCharacterIterator.TextAttribute.DY);
            if (x!= null && !x.isNaN()) {
                if (i==0) {
                    if (applyOffset) {
                        curr_x_pos = (float) offset.getX();
                    } else {
                        curr_x_pos = x.floatValue();
                        init_x_pos = curr_x_pos;
                    }
                } else {
                    curr_x_pos = x.floatValue();
                }
            } else if (dx != null && !dx.isNaN()) {
                curr_x_pos += dx.floatValue();
            }

            if (y != null && !y.isNaN()) {
                if (i==0) {
                    if (applyOffset) {
                        curr_y_pos = (float) offset.getY();
                    } else {
                        curr_y_pos = y.floatValue();
                        init_y_pos = curr_y_pos;
                    }
                } else {
                    curr_y_pos = y.floatValue();
                }
            } else if (dy != null && !dy.isNaN()) {
                curr_y_pos += dy.floatValue();
            } else if (i>0) {
                curr_y_pos += gp[i*2 + 1]-gp[i*2 - 1];
            }

            glyphPositions[i] = new Point2D.Float(curr_x_pos,  curr_y_pos);
            gv.setGlyphPosition(i, glyphPositions[i]);
            //System.out.print(ch);
            //System.out.print("["+curr_x_pos+","+curr_y_pos+"]");
            curr_x_pos += (float) gv.getGlyphMetrics(i).getAdvance();
            ch = aci.next();
            ++i;
        }
        gv.setGlyphPosition(i, new Point2D.Float(curr_x_pos, curr_y_pos));
        //System.out.println();

        advance = new Point2D.Float((float) (curr_x_pos-offset.getX()),
                                    (float) (curr_y_pos-offset.getY()));

        offset = new Point2D.Float(init_x_pos, init_y_pos);

    }

}
