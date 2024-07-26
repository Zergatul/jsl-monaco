package com.zergatul.scripting.monaco;

import com.zergatul.scripting.InternalException;
import com.zergatul.scripting.binding.BinderOutput;
import com.zergatul.scripting.binding.nodes.*;
import com.zergatul.scripting.compiler.CompilerContext;
import com.zergatul.scripting.parser.NodeType;
import com.zergatul.scripting.symbols.*;
import com.zergatul.scripting.type.*;

import java.util.ArrayList;
import java.util.List;

public class CompletionProvider {

    private final DocumentationProvider documentationProvider;

    public CompletionProvider(DocumentationProvider documentationProvider) {
        this.documentationProvider = documentationProvider;
    }

    public Suggestion[] get(BinderOutput output, int line, int column) {
        BoundCompilationUnitNode unit = output.unit();
        CompletionContext completionContext = getCompletionContext(unit, line, column);

        List<Suggestion> suggestions = new ArrayList<>();

        boolean canStatic = false;
        boolean canVoid = false;
        boolean canType = false;
        boolean canSymbol = false;
        boolean canStatement = false;
        boolean canExpression = false;
        if (completionContext.entry == null) {
            switch (completionContext.type) {
                case NO_CODE -> {
                    canStatic = true;
                    canVoid = canType = true;
                    canStatement = true;
                }
                case BEFORE_FIRST -> {
                    canStatic = true;
                    canVoid = canType = unit.variables.variables.isEmpty();
                    canStatement = unit.variables.variables.isEmpty() && unit.functions.functions.isEmpty();
                }
                case AFTER_LAST -> {
                    canStatic = unit.functions.functions.isEmpty() && unit.statements.statements.isEmpty();
                    canVoid = canType = unit.statements.statements.isEmpty();
                    canStatement = true;
                }
            }
        } else {
            switch (completionContext.entry.node.getNodeType()) {
                case COMPILATION_UNIT -> {
                    if (completionContext.prev == null) {
                        canStatic = true;
                        canVoid = canType = true;
                        if (completionContext.next == null || completionContext.next.getNodeType() == NodeType.STATEMENTS_LIST) {
                            canStatement = true;
                        }
                    } else if (completionContext.prev.getNodeType() == NodeType.STATIC_VARIABLES_LIST) {
                        canStatic = true;
                        canVoid = canType = true;
                        if (completionContext.next == null || completionContext.next.getNodeType() == NodeType.STATEMENTS_LIST) {
                            canStatement = true;
                        }
                    } else if (completionContext.prev.getNodeType() == NodeType.FUNCTIONS_LIST) {
                        canVoid = canType = true;
                        canStatement = true;
                    }
                }
                case STATEMENTS_LIST -> {
                    canStatement = true;
                }
                case PROPERTY_ACCESS_EXPRESSION -> {
                    BoundPropertyAccessExpressionNode node = (BoundPropertyAccessExpressionNode) completionContext.entry.node;
                    SType type = node.callee.type;
                    if (node.property instanceof UnknownPropertyReference) {
                        String partial = node.name;
                        type.getInstanceProperties().stream()
                                .filter(p -> p.getName().toLowerCase().startsWith(partial.toLowerCase()))
                                .forEach(p -> suggestions.add(documentationProvider.getPropertySuggestion(p)));
                        type.getInstanceMethods().stream()
                                .filter(m -> m.getName().toLowerCase().startsWith(partial.toLowerCase()))
                                .forEach(m -> suggestions.add(documentationProvider.getMethodSuggestion(m)));
                    }
                }
                default -> {
                    throw new InternalException();
                }
            }
        }

        if (canStatic) {
            suggestions.add(documentationProvider.getStaticKeywordSuggestion());
        }
        if (canVoid) {
            suggestions.add(documentationProvider.getVoidKeywordSuggestion());
        }
        if (canType) {
            for (SType type : new SType[] { SBoolean.instance, SInt.instance, SChar.instance, SFloat.instance, SString.instance }) {
                suggestions.add(documentationProvider.getTypeSuggestion(type));
            }
        }
        if (canSymbol || canStatement) {
            suggestions.addAll(getSymbols(output, completionContext, line, column));
        }
        if (canStatement) {
            // TODO: break/continue
            suggestions.addAll(documentationProvider.getCommonStatementStartSuggestions());
        }

        return suggestions.toArray(Suggestion[]::new);
    }

    private CompletionContext getCompletionContext(BoundCompilationUnitNode unit, int line, int column) {
        SearchEntry entry = find(null, unit, line, column);
        if (entry == null) {
            if (unit.getRange().isAfter(line, column)) {
                return new CompletionContext(ContextType.BEFORE_FIRST);
            }
            if (unit.getRange().isBefore(line, column)) {
                return new CompletionContext(ContextType.AFTER_LAST);
            }
            return new CompletionContext(ContextType.NO_CODE);
        }

        List<BoundNode> children = entry.node.getChildren();
        for (int i = -1; i < children.size(); i++) {
            if (i < 0 || children.get(i).getRange().isBefore(line, column)) {
                if (i + 1 >= children.size() || children.get(i + 1).getRange().isAfter(line, column)) {
                    return new CompletionContext(
                            entry,
                            i >= 0 ? children.get(i) : null,
                            i < children.size() - 1 ? children.get(i + 1) : null);
                }
            }
        }

        throw new InternalException();
    }

    private List<Suggestion> getSymbols(BinderOutput output, CompletionContext context, int line, int column) {
        List<Suggestion> list = new ArrayList<>();

        addStaticConstants(list, output.context());

        if (context.entry == null) {
            if (context.type == ContextType.AFTER_LAST) {
                addStaticVariables(list, output.unit().variables);
                addFunctions(list, output.unit().functions);
                addLocalVariables(list, output.unit().statements.statements);
            }
        } else {
            switch (context.entry.node.getNodeType()) {
                case COMPILATION_UNIT -> {
                    if (context.prev != null) {
                        if (context.prev.getNodeType() == NodeType.STATIC_VARIABLES_LIST) {
                            addStaticVariables(list, output.unit().variables);
                        } else if (context.prev.getNodeType() == NodeType.FUNCTIONS_LIST) {
                            addStaticVariables(list, output.unit().variables);
                            addFunctions(list, output.unit().functions);
                        }
                    }
                }
                case STATEMENTS_LIST -> {
                    addStaticVariables(list, output.unit().variables);
                    addFunctions(list, output.unit().functions);
                    addLocalVariables(list, getStatementsPriorTo(context.entry.node, context.prev));
                }
                default -> {
                    throw new InternalException();
                }
            }
        }

        return list;
    }

    private List<BoundStatementNode> getStatementsPriorTo(BoundNode parent, BoundNode prev) {
        if (prev == null) {
            return List.of();
        }

        List<BoundStatementNode> nodes = new ArrayList<>();
        List<BoundNode> children = parent.getChildren();
        for (BoundNode node : children) {
            if (node instanceof BoundStatementNode statement) {
                nodes.add(statement);
            }
            if (node == prev) {
                break;
            }
        }

        return nodes;
    }

    private void addStaticConstants(List<Suggestion> suggestions, CompilerContext context) {
        for (Symbol symbol : context.getStaticSymbols()) {
            if (symbol instanceof StaticFieldConstantStaticVariable constant) {
                suggestions.add(documentationProvider.getStaticVariableSuggestion(constant));
            }
        }
    }

    private void addStaticVariables(List<Suggestion> suggestions, BoundStaticVariablesListNode node) {
        for (BoundVariableDeclarationNode declaration : node.variables) {
            suggestions.add(documentationProvider.getStaticVariableSuggestion((StaticVariable) declaration.name.symbol));
        }
    }

    private void addFunctions(List<Suggestion> suggestions, BoundFunctionsListNode node) {
        for (BoundFunctionNode function : node.functions) {
            suggestions.add(documentationProvider.getFunctionSuggestion((Function) function.name.symbol));
        }
    }

    private void addLocalVariables(List<Suggestion> suggestions, List<BoundStatementNode> statements) {
        for (BoundStatementNode statement : statements) {
            if (statement instanceof BoundVariableDeclarationNode declaration) {
                suggestions.add(documentationProvider.getLocalVariableSuggestion((LocalVariable) declaration.name.symbol));
            }
        }
    }

    private SearchEntry find(SearchEntry parent, BoundNode node, int line, int column) {
        if (node.getRange().contains(line, column)) {
            SearchEntry entry = new SearchEntry(parent, node);
            for (BoundNode child : node.getChildren()) {
                if (child.getRange().contains(line, column)) {
                    return find(entry, child, line, column);
                }
            }
            return entry;
        } else {
            return null;
        }
    }

    private record SearchEntry(SearchEntry parent, BoundNode node) {}

    private static class CompletionContext {

        public final ContextType type;
        public final SearchEntry entry;
        public final BoundNode prev;
        public final BoundNode next;

        public CompletionContext(ContextType type) {
            this.type = type;
            this.entry = null;
            this.prev = null;
            this.next = null;
        }

        public CompletionContext(SearchEntry entry, BoundNode prev, BoundNode next) {
            this.type = ContextType.WITHIN;
            this.entry = entry;
            this.prev = prev;
            this.next = next;
        }
    }

    private enum ContextType {
        NO_CODE,
        BEFORE_FIRST,
        AFTER_LAST,
        WITHIN
    }
}