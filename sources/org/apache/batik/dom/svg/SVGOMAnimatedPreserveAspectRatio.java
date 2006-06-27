/*

   Copyright 2004  The Apache Software Foundation 

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */
package org.apache.batik.dom.svg;

import org.apache.batik.util.SVGConstants;

import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.svg.SVGAnimatedPreserveAspectRatio;
import org.w3c.dom.svg.SVGPreserveAspectRatio;

/**
 * This class implements the {@link SVGAnimatedPreserveAspectRatio} interface.
 *
 * @author <a href="mailto:tonny@kiyut.com">Tonny Kohar</a>
 * @version $Id$
 */
public class SVGOMAnimatedPreserveAspectRatio
        extends AbstractSVGAnimatedValue
        implements SVGAnimatedPreserveAspectRatio {

    /**
     * The base value.
     */
    protected BaseSVGPARValue baseVal;

    /**
     * The animated value.
     */
    protected AnimSVGPARValue animVal;

    /**
     * Whether the value is changing.
     */
    protected boolean changing;

    /**
     * Creates a new SVGOMAnimatedPreserveAspectRatio.
     * @param elt The associated element.
     */
    public SVGOMAnimatedPreserveAspectRatio(AbstractElement elt) {
        super(elt, null, SVGConstants.SVG_PRESERVE_ASPECT_RATIO_ATTRIBUTE);
    }

    /**
     * <b>DOM</b>: Implements {@link SVGAnimatedPreserveAspectRatio#getBaseVal()}.
     */
    public SVGPreserveAspectRatio getBaseVal() {
        if (baseVal == null) {
            baseVal = new BaseSVGPARValue();
        }
        return baseVal;
    }

    /**
     * <b>DOM</b>: Implements {@link SVGAnimatedPreserveAspectRatio#getAnimVal()}.
     */
    public SVGPreserveAspectRatio getAnimVal() {
        if (animVal == null) {
            animVal = new AnimSVGPARValue();
        }
        return animVal;
    }

    /**
     * Sets the animated value.
     */
    public void setAnimatedValue(short align, short meetOrSlice) {
        if (animVal == null) {
            animVal = new AnimSVGPARValue();
        }
        hasAnimVal = true;
        animVal.setAnimatedValue(align, meetOrSlice);
        fireAnimatedAttributeListeners();
    }

    /**
     * Resets the animated value.
     */
    public void resetAnimatedValue() {
        hasAnimVal = false;
        fireAnimatedAttributeListeners();
    }

    /**
     * Called when an Attr node has been added.
     */
    public void attrAdded(Attr node, String newv) {
        if (!changing && baseVal != null) {
            baseVal.invalidate();
        }
        // XXX Notify baseVal listeners (if we need them).
        if (!hasAnimVal) {
            fireAnimatedAttributeListeners();
        }
    }

    /**
     * Called when an Attr node has been modified.
     */
    public void attrModified(Attr node, String oldv, String newv) {
        if (!changing && baseVal != null) {
            baseVal.invalidate();
        }
        // XXX Notify baseVal listeners (if we need them).
        if (!hasAnimVal) {
            fireAnimatedAttributeListeners();
        }
    }

    /**
     * Called when an Attr node has been removed.
     */
    public void attrRemoved(Attr node, String oldv) {
        if (!changing && baseVal != null) {
            baseVal.invalidate();
        }
        // XXX Notify baseVal listeners (if we need them).
        if (!hasAnimVal) {
            fireAnimatedAttributeListeners();
        }
    }

    /**
     * This class represents the SVGPreserveAspectRatio returned by {@link
     * #getBaseVal()}.
     */
    public class BaseSVGPARValue extends AbstractSVGPreserveAspectRatio {
        
        /**
         * Create a DOMException.
         */
        protected DOMException createDOMException(short type, String key,
                                                  Object[] args) {
            return element.createDOMException(type, key, args);
        }

        /**
         * Sets the associated DOM attribute.
         */
        protected void setAttributeValue(String value) throws DOMException {
            try {
                changing = true;
                element.setAttributeNS
                    (null, SVGConstants.SVG_PRESERVE_ASPECT_RATIO_ATTRIBUTE,
                     value);
            } finally {
                changing = false;
            }
        }

        /**
         * Re-reads the DOM attribute value.
         */
        protected void invalidate() {
            String s = element.getAttributeNS
                (null, SVGConstants.SVG_PRESERVE_ASPECT_RATIO_ATTRIBUTE);
            setValueAsString(s);
        }
    }

    /**
     * This class represents the SVGPreserveAspectRatio returned by {@link
     * #getAnimVal()}.
     */
    public class AnimSVGPARValue extends AbstractSVGPreserveAspectRatio {
        
        /**
         * Create a DOMException.
         */
        protected DOMException createDOMException(short type, String key,
                                                  Object[] args) {
            return element.createDOMException(type, key, args);
        }

        /**
         * Sets the associated DOM attribute.  Does nothing, since animated
         * values aren't reflected in the DOM.
         */
        protected void setAttributeValue(String value) throws DOMException {
        }

        /**
         * <b>DOM</b>: Implements {@link SVGPreservAspectRatio#getAlign()}.
         */
        public short getAlign() {
            if (hasAnimVal) {
                return super.getAlign();
            }
            return getBaseVal().getAlign();
        }
        
        /**
         * <b>DOM</b>: Implements {@link SVGPreservAspectRatio#getMeetOrSlice()}.
         */
        public short getMeetOrSlice() {
            if (hasAnimVal) {
                return super.getMeetOrSlice();
            }
            return getBaseVal().getMeetOrSlice();
        }

        /**
         * <b>DOM</b>: Implements {@link SVGPreservAspectRatio#setAlign(short)}.
         */
        public void setAlign(short align) {
            throw element.createDOMException
                (DOMException.NO_MODIFICATION_ALLOWED_ERR,
                 "readonly.preserve.aspect.ratio", null);
        }

        /**
         * <b>DOM</b>: Implements {@link SVGPreservAspectRatio#setMeetOrSlice(short)}.
         */
        public void setMeetOrSlice(short meetOrSlice) {
            throw element.createDOMException
                (DOMException.NO_MODIFICATION_ALLOWED_ERR,
                 "readonly.preserve.aspect.ratio", null);
        }

        /**
         * Updates the animated value.
         */
        protected void setAnimatedValue(short align, short meetOrSlice) {
            this.align = align;
            this.meetOrSlice = meetOrSlice;
        }
    }
}
