/** 
 * Copyright (c) 2009-2011, The HATS Consortium. All rights reserved. 
 * This file is licensed under the terms of the Modified BSD License.
 */
package abs.backend.java.lib.types;

public interface ABSValue extends ABSType {
    ABSBool eq(ABSValue o);

    ABSBool notEq(ABSValue o);

    boolean isDataType();

    boolean isReference();

}
