package com.zergatul.scripting.monaco;

import com.zergatul.scripting.binding.BinderOutput;
import com.zergatul.scripting.binding.nodes.*;
import com.zergatul.scripting.compiler.Function;
import com.zergatul.scripting.compiler.LocalVariable;
import com.zergatul.scripting.compiler.StaticVariable;
import com.zergatul.scripting.parser.NodeType;
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
        Entry entry = find(null, unit, line, column);
        if (entry == null) {
            entry = new Entry(null, unit);
        }

        boolean canStatic = false;
        boolean canVoid = false;
        boolean canType = false;
        boolean canSymbol = false;
        boolean canStatement = false;
        Entry current = entry;

        loop:
        while (current != null) {
            switch (current.node.getNodeType()) {
                case COMPILATION_UNIT -> {
                    BoundNode prev = getPrev(current.node, line, column);
                    BoundNode next = getNext(current.node, line, column);

                    if (prev != null) {
                        if (prev.getNodeType() == NodeType.STATIC_VARIABLES_LIST) {
                            canStatic = true;
                            canVoid = true;
                            canType = true;
                        }
                        if (prev.getNodeType() == NodeType.FUNCTIONS_LIST) {
                            canVoid = true;
                            canType = true;
                        }
                        if (prev.getNodeType() == NodeType.STATEMENTS_LIST) {
                            canSymbol = true;
                            canStatement = true;
                        }
                    }

                    if (next != null) {
                        if (next.getNodeType() == NodeType.STATIC_VARIABLES_LIST) {
                            canStatic = true;
                        }
                        if (next.getNodeType() == NodeType.FUNCTIONS_LIST) {
                            canStatic = true;
                            canVoid = true;
                            canType = true;
                        }
                        if (next.getNodeType() == NodeType.STATEMENTS_LIST) {
                            canVoid = true;
                            canType = true;
                            canSymbol = true;
                            canStatement = true;
                        }
                    }
                }
                case STATEMENTS_LIST -> {
                    canStatement = true;
                    break loop;
                }
                default -> {}
            }

            current = current.parent;
        }

        List<Suggestion> suggestions = new ArrayList<>();
        if (canStatic) {
            suggestions.add(documentationProvider.getStaticKeywordSuggestion());
        }
        if (canVoid) {
            suggestions.add(documentationProvider.getVoidKeywordSuggestion());
        }
        if (canType || canStatement) {
            for (SType type : new SType[] { SBoolean.instance, SInt.instance, SChar.instance, SFloat.instance, SString.instance }) {
                suggestions.add(documentationProvider.getTypeSuggestion(type));
            }
        }
        if (canSymbol || canStatement) {
            suggestions.addAll(getSymbols(entry, line, column));
        }
        if (canStatement) {
            // TODO: break/continue
            suggestions.addAll(documentationProvider.getCommonStatementStartSuggestions());
        }

        return suggestions.toArray(Suggestion[]::new);
    }

    private List<Suggestion> getSymbols(Entry entry, int line, int column) {
        List<Suggestion> list = new ArrayList<>();

        if (entry.node instanceof BoundCompilationUnitNode unit) {
            BoundNode prev = getPrev(entry.node, line, column);
            if (prev.getNodeType() == NodeType.STATIC_VARIABLES_LIST) {
                for (BoundNode node : unit.variables.variables) {
                    list.addAll(getSymbols(node));
                }
            }
            if (prev.getNodeType() == NodeType.FUNCTIONS_LIST) {
                for (BoundNode node : unit.variables.variables) {
                    list.addAll(getSymbols(node));
                }
                for (BoundNode node : unit.functions.functions) {
                    list.addAll(getSymbols(node));
                }
            }
            if (prev.getNodeType() == NodeType.STATEMENTS_LIST) {
                for (BoundNode node : unit.variables.variables) {
                    list.addAll(getSymbols(node));
                }
                for (BoundNode node : unit.functions.functions) {
                    list.addAll(getSymbols(node));
                }
                for (BoundNode node : unit.statements.statements) {
                    list.addAll(getSymbols(node));
                }
            }
            return list;
        }

        // find previous inside itself
        BoundNode prev = getPrev(entry.node, line, column);
        if (prev != null) {
            for (BoundNode child : entry.node.getChildren()) {
                list.addAll(getSymbols(child));
                if (child == prev) {
                    break;
                }
            }
        } else {
            // find previous inside parent
            if (entry.parent != null) {
                BoundNode parent = entry.parent.node;
                BoundNode parentPrev = getPrev(parent, line, column);
                for (BoundNode child : parent.getChildren()) {
                    list.addAll(getSymbols(child));
                    if (child == parentPrev) {
                        break;
                    }
                }
            }
        }

        if (entry.parent != null) {
            list.addAll(getSymbols(entry.parent, line, column));
        }

        return list;
    }

    private List<Suggestion> getSymbols(BoundNode node) {
        if (node instanceof BoundFunctionNode function) {
            return List.of(documentationProvider.getFunctionSuggestion((Function) function.name.symbol));
        }
        if (node instanceof BoundParameterListNode parameters) {
            return parameters.parameters.stream()
                    .map(p -> documentationProvider.getLocalVariableSuggestion((LocalVariable) p.getName().symbol))
                    .toList();
        }
        if (node instanceof BoundVariableDeclarationNode declaration) {
            if (declaration.name.symbol instanceof LocalVariable variable) {
                return List.of(documentationProvider.getLocalVariableSuggestion(variable));
            }
            if (declaration.name.symbol instanceof StaticVariable variable) {
                return List.of(documentationProvider.getStaticVariableSuggestion(variable));
            }
        }
        return List.of();
    }

    private Entry find(Entry parent, BoundNode node, int line, int column) {
        if (node.getRange().contains(line, column)) {
            Entry entry = new Entry(parent, node);
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

    private BoundNode getPrev(BoundNode node, int line, int column) {
        BoundNode previous = null;
        for (BoundNode child : node.getChildren()) {
            if (child.getRange().isBefore(line, column)) {
                previous = child;
            }
        }
        return previous;
    }

    private BoundNode getNext(BoundNode node, int line, int column) {
        for (BoundNode child : node.getChildren()) {
            if (child.getRange().isAfter(line, column)) {
                return child;
            }
        }
        return null;
    }

    private record Entry(Entry parent, BoundNode node) {}
}