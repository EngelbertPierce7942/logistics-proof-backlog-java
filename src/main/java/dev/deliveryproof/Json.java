package dev.deliveryproof;

import java.util.LinkedHashMap;
import java.util.Map;

final class Json {
    private Json() {}

    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    static Object parse(String source) {
        Parser parser = new Parser(source);
        Object value = parser.value();
        parser.space();
        if (parser.index != source.length()) throw new IllegalArgumentException("trailing JSON content");
        return value;
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) { this.source = source; }

        private Object value() {
            space();
            if (index >= source.length()) throw new IllegalArgumentException("empty JSON");
            return switch (source.charAt(index)) {
                case '{' -> object();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> values = new LinkedHashMap<>();
            index++;
            space();
            if (take('}')) return values;
            while (true) {
                space();
                String key = string();
                space();
                expect(':');
                values.put(key, value());
                space();
                if (take('}')) return values;
                expect(',');
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (index < source.length()) {
                char c = source.charAt(index++);
                if (c == '"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                if (index >= source.length()) throw new IllegalArgumentException("bad JSON escape");
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (index + 4 > source.length()) throw new IllegalArgumentException("bad unicode escape");
                        out.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("bad JSON escape");
                }
            }
            throw new IllegalArgumentException("unterminated JSON string");
        }

        private Object number() {
            int start = index;
            while (index < source.length() && "-+0123456789.eE".indexOf(source.charAt(index)) >= 0) index++;
            String token = source.substring(start, index);
            try { return token.contains(".") || token.contains("e") || token.contains("E")
                    ? Double.parseDouble(token) : Long.parseLong(token); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("bad JSON value", e); }
        }

        private Object literal(String token, Object value) {
            if (!source.startsWith(token, index)) throw new IllegalArgumentException("bad JSON literal");
            index += token.length();
            return value;
        }

        private void space() { while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++; }
        private boolean take(char c) { if (index < source.length() && source.charAt(index) == c) { index++; return true; } return false; }
        private void expect(char c) { if (!take(c)) throw new IllegalArgumentException("expected " + c); }
    }
}
