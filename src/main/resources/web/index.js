import * as monaco from 'https://cdn.jsdelivr.net/npm/monaco-editor@0.48.0/+esm';

(async () => {
    const languageId = 'cheatutils-scripting-language';
    monaco.languages.register({ id: languageId });

    const get = async url => {
        const response = await fetch(url);
        return await response.json();
    };
    const post = async (url, body) => {
        const response = await fetch(url, { method: 'POST', body: JSON.stringify(body) });
        return await response.json();
    };

    const tokens = await get('/code/tokens');
    const nodes = await get('/code/nodes');

    const setDiagnostics = async (model) => {
        let diagnostics = await post('/code/diagnostics', {
            code: model.getValue(),
            type: ''
        });
        let tokens = [];
        for (let diagnostic of diagnostics) {
            tokens.push({
                startLineNumber: diagnostic.range.line1,
                startColumn: diagnostic.range.colum1,
                endLineNumber: diagnostic.range.line2,
                endColumn: diagnostic.range.column2,
                message: diagnostic.message,
                severity: monaco.MarkerSeverity.Error
            });
        }
        monaco.editor.setModelMarkers(model, 'owner', tokens);
    };

    monaco.languages.registerDocumentSemanticTokensProvider(languageId, {
        getLegend() {
            return {
                tokenTypes: tokens,
                tokenModifiers: [],
            };
        },
        async provideDocumentSemanticTokens(model, lastResultId, token) {
            let tokenize = post('/code/tokenize', model.getValue());
            setDiagnostics(model);
            let lexerOutput = await tokenize;
            let result = [];
            let prevToken = { range: { line1: 1, column1: 1, length: 0 } };
            for (let token of lexerOutput.tokens.list) {
                let type = tokens[token.type];
                if (type == 'WHITESPACE' || type == 'LINE_BREAK') {
                    continue;
                }
                /*
                    Line number (0-indexed, and offset from the previous line)
                    Column position (0-indexed, and offset from the previous column, unless this is the beginning of a new line)
                    Token length
                    Token type index (0-indexed into the tokenTypes array defined in getLegend)
                    Modifier index (0-indexed into the tokenModifiers array defined in getLegend)
                */
                result.push(
                    token.range.line1 - prevToken.range.line1,
                    token.range.line1 == prevToken.range.line1 ? token.range.column1 - (prevToken.range.column1) : token.range.column1 - 1,
                    token.range.length,
                    token.type,
                    0);
    
                prevToken = token;
            }
            return {
                data: result
            };
        },
        releaseDocumentSemanticTokens(resultId) {
    
        }
    });

    monaco.languages.registerHoverProvider(languageId, {
        async provideHover(model, position) {
            const hover = await post('/code/hover', {
                code: model.getValue(),
                type: "",
                line: position.lineNumber,
                column: position.column
            });
            if (hover == null) {
                return null;
            }
            return {
                range: new monaco.Range(
                    hover.range.line1,
                    hover.range.column1,
                    hover.range.line2,
                    hover.range.column2),
                contents: hover.content.map(s => {
                    return {
                        value: s,
                        isTrusted: true,
                        supportHtml: true
                    };
                })
            };
        }
    });

    monaco.languages.registerDefinitionProvider(languageId, {
        async provideDefinition(model, position, token) {
            const range = await post('/code/definition', {
                code: model.getValue(),
                type: "",
                line: position.lineNumber,
                column: position.column
            });
            if (range == null) {
                return null;
            }
            return {
                uri: model.uri,
                range: new monaco.Range(
                    range.line1,
                    range.column1,
                    range.line2,
                    range.column2)
            };
        }
    });

    /*monaco.languages.registerCompletionItemProvider(languageId, {
        async provideCompletionItems(model, position, context, token) {
            const suggestions = await post('/code/completion', {
                code: model.getValue(),
                type: "",
                line: position.lineNumber,
                column: position.column
            });
            return {
                suggestions: suggestions.map(s => {
                    return {
                        ...s,
                        kind: monaco.languages.CompletionItemKind[s.kind]
                    };
                })
            };
        }
    });*/

    monaco.editor.defineTheme('cheatutils-scripting-language-dark', {
        base: 'vs-dark',
        inherit: true,
        colors: {},
        rules: await get('/code/token-rules')
    });

    monaco.editor.create(document.getElementById('container'), {
        value:
            'static int ww = 900;\n\n' +
            'int func1(int x) {\n    return x + ww + 1;\n}\n\n' +
            'freeCam.toggle();\nint x = func1(12345);\nint y = x + 333;\nchar ch = \'!\';\nfloat a = 1.43;\nfloat b = 100;\nstring s = "text";\nboolean bb = false;\nif (x > y) {\n    main.chat("abc");\n}\n' +
            'boolean bbb = s.contains("ex");',
        language: languageId,
        'renderWhitespace': "all",
        'semanticHighlighting.enabled': true
    });
    monaco.editor.setTheme('cheatutils-scripting-language-dark');
})();