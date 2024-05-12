package com.zergatul.scripting.monaco;

import com.zergatul.scripting.TextRange;
import com.zergatul.scripting.binding.nodes.*;
import com.zergatul.scripting.compiler.*;
import com.zergatul.scripting.type.*;
import com.zergatul.scripting.type.operation.BinaryOperation;

import java.lang.reflect.Method;
import java.util.List;

public class HoverProvider {

    private final Theme theme;

    public HoverProvider(Theme theme) {
        this.theme = theme;
    }

    public HoverResponse get(BoundNode node) {
        if (node == null) {
            return null;
        }

        TextRange range = node.getRange();
        return switch (node.getNodeType()) {
            case BOOLEAN_LITERAL -> getBoolean(range);
            case INTEGER_LITERAL -> getInt(range);
            case CHAR_LITERAL -> getChar(range);
            case FLOAT_LITERAL -> getFloat(range);
            case STRING_LITERAL -> getString(range);
            case PREDEFINED_TYPE -> {
                SType type = ((BoundPredefinedTypeNode) node).type;
                if (type == SBoolean.instance) {
                    yield getBoolean(range);
                } else if (type == SInt.instance) {
                    yield getInt(range);
                } else if (type == SChar.instance) {
                    yield getChar(range);
                } else if (type == SFloat.instance) {
                    yield getFloat(range);
                } else if (type == SString.instance) {
                    yield getString(range);
                } else {
                    yield null;
                }
            }
            case NAME_EXPRESSION -> {
                BoundNameExpressionNode name = (BoundNameExpressionNode) node;
                if (name.symbol instanceof LocalParameter local) {
                    String line = description("(parameter)") + " " + type(local.getType()) + " " + description(local.getName());
                    yield new HoverResponse(line, range);
                } else if (name.symbol instanceof LocalRefParameter local) {
                    String line = description("(parameter)") + " " + predefinedType("ref") + " " + type(local.getType()) + " " + description(local.getName());
                    yield new HoverResponse(line, range);
                } else if (name.symbol instanceof LocalVariable local) {
                    String line = description("(local variable)") + " " + type(local.getType()) + " " + description(local.getName());
                    yield new HoverResponse(line, range);
                } else if (name.symbol instanceof StaticFieldConstantStaticVariable field) {
                    String line = description("(external static constant)") + " " + type(field.getType()) + " " + description(field.getName());
                    yield new HoverResponse(line, range);
                } else if (name.symbol instanceof StaticVariable staticVariable) {
                    String line = description("(static variable)") + " " + type(staticVariable.getType()) + " " + description(staticVariable.getName());
                    yield new HoverResponse(line, range);
                } else if (name.symbol instanceof Function function) {
                    SFunction type = function.getFunctionType();
                    StringBuilder sb = new StringBuilder();
                    sb.append(type(type.getReturnType())).append(' ');
                    sb.append(span(theme.getMethodColor(), function.getName()));
                    sb.append(description("("));
                    List<MethodParameter> parameters = type.getParameters();
                    for (int i = 0; i < parameters.size(); i++) {
                        sb.append(type(parameters.get(i).type())).append(' ');
                        sb.append(parameter(parameters.get(i).name()));
                        if (i < parameters.size() - 1) {
                            sb.append(description(", "));
                        }
                    }
                    sb.append(description(")"));
                    yield new HoverResponse(sb.toString(), range);
                } else {
                    yield null;
                }
            }
            case METHOD -> {
                BoundMethodNode methodNode = (BoundMethodNode) node;
                MethodReference methodReference = methodNode.method;
                StringBuilder sb = new StringBuilder();
                sb.append(type(methodReference.getReturn())).append(' ');
                sb.append(type(methodReference.getOwner()));
                sb.append(description("."));
                sb.append(span(theme.getMethodColor(), methodReference.getName()));
                sb.append(description("("));
                List<MethodParameter> parameters = methodReference.getParameters();
                for (int i = 0; i < parameters.size(); i++) {
                    sb.append(type(parameters.get(i).type())).append(' ');
                    sb.append(parameter(parameters.get(i).name()));
                    if (i < parameters.size() - 1) {
                        sb.append(description(", "));
                    }
                }
                sb.append(description(")"));
                yield new HoverResponse(sb.toString(), range);
            }
            case BINARY_OPERATOR -> {
                BoundBinaryOperatorNode operator = (BoundBinaryOperatorNode) node;
                BinaryOperation operation = operator.operation;
                String line = type(operation.type) + " " + description(operation.operator.toString()) + description("(") + type(operation.left) + " " + parameter("left") + description(",") + " " + type(operation.right) + " " + parameter("right") + description(")");
                yield new HoverResponse(line, range);
            }
            default -> null;
        };
    }

    private HoverResponse getBoolean(TextRange range) {
        return new HoverResponse(List.of(predefinedType("boolean"), description("true or false value")), range);
    }

    private HoverResponse getInt(TextRange range) {
        return new HoverResponse(List.of(predefinedType("int"), description("32-bit signed integer")), range);
    }

    private HoverResponse getChar(TextRange range) {
        return new HoverResponse(List.of(predefinedType("char"), description("Single character")), range);
    }

    private HoverResponse getFloat(TextRange range) {
        return new HoverResponse(List.of(predefinedType("float"), description("Double-precision floating-point number")), range);
    }

    private HoverResponse getString(TextRange range) {
        return new HoverResponse(List.of(predefinedType("string"), description("Text as sequence of characters")), range);
    }

    private String type(SType type) {
        if (type instanceof SPredefinedType) {
            return predefinedType(type.toString());
        } else if (type instanceof SClassType) {
            Class<?> clazz = type.getJavaClass();
            if (clazz.getName().startsWith("com.zergatul.scripting.monaco")) {
                return span(theme.getTypeColor(), clazz.getSimpleName());
            } else {
                return span(theme.getTypeColor(), clazz.getName());
            }
        } else {
            return "TODO";
        }
    }

    private String predefinedType(String text) {
        return span(theme.getPredefinedTypeColor(), text);
    }

    private String description(String text) {
        return span(theme.getDescriptionColor(), text);
    }

    private String parameter(String text) {
        return span(theme.getParameterColor(), text);
    }

    private String span(String color, String text) {
        return String.format("<span style=\"color:#%s;\">%s</span>", color, escapeHtml(text));
    }

    public static String escapeHtml(String s) {
        StringBuilder out = new StringBuilder(Math.max(16, s.length()));
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 127 || c == '"' || c == '\'' || c == '<' || c == '>' || c == '&') {
                out.append("&#");
                out.append((int) c);
                out.append(';');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public record HoverResponse(List<String> content, TextRange range) {
        public HoverResponse(String line, TextRange range) {
            this(List.of(line), range);
        }
    }
}