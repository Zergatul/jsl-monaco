package com.zergatul.scripting.monaco;

public enum CompletionItemKind {
    METHOD("Method"),
    PROPERTY("Property"),
    VARIABLE("Variable"),
    KEYWORD("Keyword"),
    FUNCTION("Function"),
    STRUCT("Struct"),
    CLASS("Class");

    private final String name;

    CompletionItemKind(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}