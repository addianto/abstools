/** 
 * Copyright (c) 2009-2011, The HATS Consortium. All rights reserved. 
 * This file is licensed under the terms of the Modified BSD License.
 */
package abs.frontend.parser;

import beaver.Symbol;
import abs.common.CompilerError;

public class SyntaxError extends ParserError {

    private static final long serialVersionUID = 1L;
    private Symbol token;

    public SyntaxError(final String message) {
        this(message, 0, 0, null);
    }

    public SyntaxError(final String message, int lineNumber, int columnNumber, Symbol token) {
        super(message, lineNumber, columnNumber);
        this.token = token;
    }

    public Symbol getToken() {
        return token;
    }
}
