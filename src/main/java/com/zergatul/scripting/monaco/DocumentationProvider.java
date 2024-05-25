package com.zergatul.scripting.monaco;

import com.zergatul.scripting.type.*;

public class DocumentationProvider {

    public String getTypeDocs(SType type) {
        if (type == SBoolean.instance) {
            return "true or false value";
        }
        if (type == SInt.instance) {
            return "32-bit signed integer";
        }
        if (type == SChar.instance) {
            return "Single character";
        }
        if (type == SFloat.instance) {
            return "Double-precision floating-point number";
        }
        if (type == SString.instance) {
            return "Text as sequence of characters";
        }
        return null;
    }

    public Suggestion getTypeSuggestion(SType type) {
        if (type instanceof SPredefinedType) {
            return new Suggestion(
                    type.toString(),
                    null,
                    getTypeDocs(type),
                    type.toString(),
                    CompletionItemKind.CLASS);
        }
        return null;
    }
}