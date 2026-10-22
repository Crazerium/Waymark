package com.crazerium.waymark.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;

/**
 * Text form of NBT in the modern style, e.g. {@code {display:{Name:"Lich Bone"},Count:3b,list:[1,2]}}.
 * Vanilla 1.7.10's NBT toString does not escape quotes and its parser has quirks, so Waymark uses its own.
 */
public final class Snbt {

    private Snbt() {}

    // ---- writing ----

    public static String write(NBTBase tag) {
        StringBuilder sb = new StringBuilder();
        write(tag, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(NBTBase tag, StringBuilder sb) {
        switch (tag.getId()) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
                // Vanilla's toString of number tags already uses the b/s/L/f/d suffixes.
                sb.append(tag);
                break;
            case 7: {
                byte[] bytes = ((NBTTagByteArray) tag).func_150292_c();
                sb.append("[B;");
                for (int i = 0; i < bytes.length; i++) sb.append(i == 0 ? "" : ",")
                    .append(bytes[i])
                    .append('b');
                sb.append(']');
                break;
            }
            case 8:
                quote(((NBTTagString) tag).func_150285_a_(), sb);
                break;
            case 9: {
                NBTTagList list = (NBTTagList) tag.copy();
                List<NBTBase> items = new ArrayList<>();
                while (list.tagCount() > 0) items.add(list.removeTag(0));
                sb.append('[');
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) sb.append(',');
                    write(items.get(i), sb);
                }
                sb.append(']');
                break;
            }
            case 10: {
                NBTTagCompound compound = (NBTTagCompound) tag;
                // Sorted keys keep files stable between saves.
                Set<String> keys = new TreeSet<>((Set<String>) compound.func_150296_c());
                sb.append('{');
                boolean first = true;
                for (String key : keys) {
                    if (!first) sb.append(',');
                    first = false;
                    if (key.matches("[A-Za-z0-9._+-]+")) sb.append(key);
                    else quote(key, sb);
                    sb.append(':');
                    write(compound.getTag(key), sb);
                }
                sb.append('}');
                break;
            }
            case 11: {
                int[] ints = ((NBTTagIntArray) tag).func_150302_c();
                sb.append("[I;");
                for (int i = 0; i < ints.length; i++) sb.append(i == 0 ? "" : ",")
                    .append(ints[i]);
                sb.append(']');
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown NBT type " + tag.getId());
        }
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\');
            sb.append(c);
        }
        sb.append('"');
    }

    // ---- parsing ----

    public static NBTTagCompound parseCompound(String text) {
        Parser p = new Parser(text);
        NBTBase tag = p.value();
        p.skipWhitespace();
        if (p.pos != text.length()) throw p.error("Unexpected trailing text");
        if (!(tag instanceof NBTTagCompound)) throw p.error("Expected a compound");
        return (NBTTagCompound) tag;
    }

    private static final class Parser {

        final String s;
        int pos;

        Parser(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at " + pos + " in: " + s);
        }

        void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        char peek() {
            skipWhitespace();
            if (pos >= s.length()) throw error("Unexpected end");
            return s.charAt(pos);
        }

        void expect(char c) {
            if (peek() != c) throw error("Expected '" + c + "'");
            pos++;
        }

        NBTBase value() {
            char c = peek();
            if (c == '{') return compound();
            if (c == '[') return list();
            if (c == '"' || c == '\'') return new NBTTagString(quoted());
            return primitive(unquoted());
        }

        NBTTagCompound compound() {
            expect('{');
            NBTTagCompound tag = new NBTTagCompound();
            if (peek() == '}') {
                pos++;
                return tag;
            }
            while (true) {
                String key = peek() == '"' || peek() == '\'' ? quoted() : unquoted();
                expect(':');
                tag.setTag(key, value());
                if (peek() == ',') {
                    pos++;
                    continue;
                }
                expect('}');
                return tag;
            }
        }

        NBTBase list() {
            expect('[');
            char first = peek();
            if ((first == 'B' || first == 'I') && pos + 1 < s.length() && s.charAt(pos + 1) == ';') {
                char kind = s.charAt(pos);
                pos += 2;
                List<Long> numbers = new ArrayList<>();
                if (peek() != ']') {
                    while (true) {
                        String n = unquoted();
                        if (n.endsWith("b") || n.endsWith("B")) n = n.substring(0, n.length() - 1);
                        numbers.add(Long.parseLong(n));
                        if (peek() == ',') {
                            pos++;
                            continue;
                        }
                        break;
                    }
                }
                expect(']');
                if (kind == 'B') {
                    byte[] bytes = new byte[numbers.size()];
                    for (int i = 0; i < bytes.length; i++) bytes[i] = numbers.get(i)
                        .byteValue();
                    return new NBTTagByteArray(bytes);
                }
                int[] ints = new int[numbers.size()];
                for (int i = 0; i < ints.length; i++) ints[i] = numbers.get(i)
                    .intValue();
                return new NBTTagIntArray(ints);
            }
            NBTTagList list = new NBTTagList();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.appendTag(value());
                if (peek() == ',') {
                    pos++;
                    continue;
                }
                expect(']');
                return list;
            }
        }

        String quoted() {
            char q = peek();
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '\\' && pos < s.length()) sb.append(s.charAt(pos++));
                else if (c == q) return sb.toString();
                else sb.append(c);
            }
            throw error("Unterminated string");
        }

        String unquoted() {
            skipWhitespace();
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-' || c == '+') pos++;
                else break;
            }
            if (start == pos) throw error("Expected a value");
            return s.substring(start, pos);
        }

        static NBTBase primitive(String v) {
            try {
                char last = Character.toLowerCase(v.charAt(v.length() - 1));
                String body = v.substring(0, v.length() - 1);
                switch (last) {
                    case 'b':
                        return new NBTTagByte(Byte.parseByte(body));
                    case 's':
                        return new NBTTagShort(Short.parseShort(body));
                    case 'l':
                        return new NBTTagLong(Long.parseLong(body));
                    case 'f':
                        return new NBTTagFloat(Float.parseFloat(body));
                    case 'd':
                        return new NBTTagDouble(Double.parseDouble(body));
                    default:
                        if (v.contains(".")) return new NBTTagDouble(Double.parseDouble(v));
                        return new NBTTagInt(Integer.parseInt(v));
                }
            } catch (NumberFormatException e) {
                if (v.equals("true")) return new NBTTagByte((byte) 1);
                if (v.equals("false")) return new NBTTagByte((byte) 0);
                return new NBTTagString(v);
            }
        }
    }
}
