package com.zergatul.scripting.monaco;

import com.zergatul.scripting.binding.BinderOutput;
import com.zergatul.scripting.binding.nodes.BoundCompilationUnitNode;
import com.zergatul.scripting.binding.nodes.BoundNode;
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

        boolean canType = false;
        boolean canSymbol = false;
        boolean canStatement = false;
        while (entry != null) {
            switch (entry.node.getNodeType()) {
                case COMPILATION_UNIT -> {
                    BoundNode prev = getPrev(entry.node, line, column);
                    BoundNode next = getNext(entry.node, line, column);

                    if (prev.getNodeType() != NodeType.INVALID_STATEMENT) {
                        canType = true;
                    }
                }
                default -> {}
            }

            entry = entry.parent;
        }

        List<Suggestion> suggestions = new ArrayList<>();
        if (canType) {
            for (SType type : new SType[] { SBoolean.instance, SInt.instance, SChar.instance, SFloat.instance, SString.instance }) {
                suggestions.add(documentationProvider.getTypeSuggestion(type));
            }
        }
        if (canStatement) {

        }

        return suggestions.toArray(Suggestion[]::new);
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