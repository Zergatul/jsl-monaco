package com.zergatul.scripting.monaco;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.zergatul.scripting.TextRange;
import com.zergatul.scripting.binding.Binder;
import com.zergatul.scripting.binding.BinderOutput;
import com.zergatul.scripting.binding.nodes.BoundNode;
import com.zergatul.scripting.compiler.CompilationParameters;
import com.zergatul.scripting.lexer.Lexer;
import com.zergatul.scripting.lexer.LexerInput;
import com.zergatul.scripting.lexer.LexerOutput;
import com.zergatul.scripting.lexer.TokenType;
import com.zergatul.scripting.parser.NodeType;
import com.zergatul.scripting.parser.Parser;
import com.zergatul.scripting.parser.ParserOutput;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Scanner;

public class Server {

    public static void main(String[] args) {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(5505), 0);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        CompilationParametersResolver resolver = new CompilationParametersResolver() {
            @Override
            public CompilationParameters resolve(String type) {
                return new CompilationParameters(Root.class);
            }
        };

        Theme theme = new DarkTheme();
        HoverProvider hoverProvider = new HoverProvider(theme);
        DefinitionProvider definitionProvider = new DefinitionProvider();

        server.createContext("/code/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/code/tokenize")) {
                    Gson gson = new GsonBuilder().create();
                    byte[] data = exchange.getRequestBody().readAllBytes();
                    String request = new String(data, Charset.defaultCharset());
                    String code = gson.fromJson(request, String.class);

                    Lexer lexer = new Lexer(new LexerInput(code));
                    LexerOutput output = lexer.lex();
                    Json.sendResponse(exchange, output);
                } else if (path.equals("/code/tokens")) {
                    Json.sendResponse(exchange, Arrays.stream(TokenType.values()).map(Enum::name).toArray());
                } else if (path.equals("/code/nodes")) {
                    Json.sendResponse(exchange, Arrays.stream(NodeType.values()).map(Enum::name).toArray());
                } else if (path.equals("/code/token-rules")) {
                    Json.sendResponse(exchange, Arrays.stream(TokenType.values()).map(type -> new TokenRule(type.name(), theme.getTokenColor(type))).toArray());
                } else if (path.equals("/code/hover")) {
                    try {
                        Gson gson = new GsonBuilder().create();
                        byte[] data = exchange.getRequestBody().readAllBytes();
                        HoverRequest request = gson.fromJson(new String(data, Charset.defaultCharset()), HoverRequest.class);

                        LexerInput lexerInput = new LexerInput(request.code);
                        Lexer lexer = new Lexer(lexerInput);
                        LexerOutput lexerOutput = lexer.lex();

                        Parser parser = new Parser(lexerOutput);
                        ParserOutput parserOutput = parser.parse();

                        Binder binder = new Binder(parserOutput, resolver.resolve(request.type).getContext());
                        BinderOutput binderOutput = binder.bind();

                        BoundNode node = find(binderOutput.unit(), request.line, request.column);
                        Json.sendResponse(exchange, hoverProvider.get(node));
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }
                } else if (path.equals("/code/definition")) {
                    Gson gson = new GsonBuilder().create();
                    byte[] data = exchange.getRequestBody().readAllBytes();
                    HoverRequest request = gson.fromJson(new String(data, Charset.defaultCharset()), HoverRequest.class);

                    LexerInput lexerInput = new LexerInput(request.code);
                    Lexer lexer = new Lexer(lexerInput);
                    LexerOutput lexerOutput = lexer.lex();

                    Parser parser = new Parser(lexerOutput);
                    ParserOutput parserOutput = parser.parse();

                    Binder binder = new Binder(parserOutput, resolver.resolve(request.type).getContext());
                    BinderOutput binderOutput = binder.bind();

                    BoundNode node = find(binderOutput.unit(), request.line, request.column);
                    Json.sendResponse(exchange, definitionProvider.get(node), TextRange.class);
                } else {
                    exchange.sendResponseHeaders(404, 0);
                }
                exchange.close();
            }
        });

        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/")) {
                    path = "/index.html";
                }

                Path filepath = Path.of(".\\src\\main\\resources\\web", path);
                if (Files.exists(filepath)) {
                    if (path.endsWith(".js")) {
                        exchange.getResponseHeaders().add("Content-Type", "text/javascript");
                    } else if (path.endsWith(".html")) {
                        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                    } else if (path.endsWith(".ttf")) {
                        exchange.getResponseHeaders().add("Content-Type", "font/ttf");
                    }

                    long size = Files.size(filepath);
                    exchange.sendResponseHeaders(200, size);
                    byte[] data = Files.readAllBytes(filepath);
                    exchange.getResponseBody().write(data);
                } else {
                    exchange.sendResponseHeaders(404, 0);
                }

                exchange.close();
            }
        });

        server.start();

        Scanner scanner = new Scanner(System.in);
        scanner.hasNext();

        System.out.println("Stopping...");
        server.stop(1);
    }

    private static BoundNode find(BoundNode node, int line, int column) {
        if (node.getRange().contains(line, column)) {
            for (BoundNode child : node.getChildren()) {
                if (child.getRange().contains(line, column)) {
                    return find(child, line, column);
                }
            }
            return node;
        } else {
            return null;
        }
    }

    public record TokenRule(String token, String foreground) {}

    public record HoverRequest(String code, String type, int line, int column) {}

    public static class Root {
        public static final MainApi main = new MainApi();
        public static final FreeCamApi freeCam = new FreeCamApi();
    }

    public static class FreeCamApi {
        public void toggle() {}
    }

    public static class MainApi {
        public void chat(String text) {}
    }
}