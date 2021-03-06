/** 
 * Copyright (c) 2009-2011, The HATS Consortium. All rights reserved. 
 * This file is licensed under the terms of the Modified BSD License.
 */
package org.abs_models.frontend.analyser;

import static org.junit.Assert.*;

import java.util.Iterator;

import org.junit.Test;

import org.abs_models.frontend.FrontendTest;
import org.abs_models.frontend.ast.Model;

public class InterfaceDeclarationTest extends FrontendTest {

    @Test
    public void trivial() {
        Model p = assertParse("interface I {} {}");
        assertTrue(!p.getErrors().containsErrors());
    }

    @Test
    public void extending() {
        Model p = assertParse("interface I {} interface J extends I {} {}");
        assertTrue(!p.getErrors().containsErrors());
    }

    @Test
    public void extendingReversed() {
        Model p = assertParse("interface J extends I {} interface I {} {}");
        assertTrue(!p.getErrors().containsErrors());
    }

    @Test
    public void extendingUndefined() {
        Model p = assertParse("interface J extends I {} {}");
        assertEquals(1,p.getErrors().getErrorCount());
        assertEndsWith(p.getErrors().getFirstError(), ErrorMessage.UNKOWN_INTERFACE.withArgs("I"));
    }

    @Test
    public void circular() {
        Model p = assertParse("interface I extends I {} {}");
        assertEquals(1,p.getErrors().getErrorCount());
        assertEndsWith(p.getErrors().getFirstError(), ErrorMessage.CYCLIC_INHERITANCE.withArgs("I"));
    }

    @Test
    public void mutuallyCircular() {
        Model p = assertParse("interface I extends J {} interface J extends I {} {}");
        assertEquals(2,p.getErrors().getErrorCount());
        Iterator<SemanticCondition> i = p.getErrors().iterator();
        assertEndsWith(i.next(), ErrorMessage.CYCLIC_INHERITANCE.withArgs("I"));
        assertEndsWith(i.next(), ErrorMessage.CYCLIC_INHERITANCE.withArgs("J"));
    }

    @Test
    public void mutuallyCircularIndirect() {
        Model p = assertParse("interface I extends J {}  interface J extends K {}  interface K extends I {}");
        assertEquals(3,p.getErrors().getErrorCount());
        Iterator<SemanticCondition> i = p.getErrors().iterator();
        assertEndsWith(i.next(), ErrorMessage.CYCLIC_INHERITANCE.withArgs("I"));
        assertEndsWith(i.next(), ErrorMessage.CYCLIC_INHERITANCE.withArgs("J"));
        assertEndsWith(i.next(), ErrorMessage.CYCLIC_INHERITANCE.withArgs("K"));
    }

    private void assertEndsWith(SemanticCondition expected, String actual) {
        assertTrue("Expected that " + expected.getHelpMessage() + " ends with " + actual, expected.getHelpMessage()
                .endsWith(actual));
    }

}
