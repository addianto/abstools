/** 
 * Copyright (c) 2009-2011, The HATS Consortium. All rights reserved. 
 * This file is licensed under the terms of the Modified BSD License.
 */
package abs.backend.java.lib.types;

public class ABSUnit extends ABSBuiltInDataType {
    public static ABSUnit UNIT = new ABSUnit();

    private ABSUnit() {
        super("Unit");
    }

}
