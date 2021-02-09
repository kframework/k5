// Copyright (c) 2014-2019 K Team. All Rights Reserved.
package org.kframework.utils;

import org.apache.commons.lang3.StringUtils;

import com.beust.jcommander.JCommander;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StringUtil {
    /**
     * Unescape the textual representation of a string specific to SDF and Maude.
     * It removes the double quote at the beginning and end, and transforms special sequence
     * of characters like "\n" into the newline character.
     */
    public static String unquoteCString(String str) {
        return unquoteCString(str, '"');
    }
    public static String unquoteCString(String str, char delimiter) {
        StringBuilder sb = new StringBuilder();
        if (str.charAt(0) != delimiter) {
            throw new IllegalArgumentException("Expected to find " + delimiter + " at the beginning of string: " + str);
        }
        if (str.charAt(str.length() - 1) != delimiter) {
            throw new IllegalArgumentException("Expected to find " + delimiter + " at the end of string: " + str);
        }
        for (int i = 1; i < str.length() - 1; i++) {
            if (str.charAt(i) == '\\') {
                if (str.charAt(i + 1) == '\\')
                    sb.append('\\');
                else if (str.charAt(i + 1) == 'n')
                    sb.append('\n');
                else if (str.charAt(i + 1) == 'r')
                    sb.append('\r');
                else if (str.charAt(i + 1) == 't')
                    sb.append('\t');
                else if (str.charAt(i + 1) == 'f')
                    sb.append('\f');
                else if (str.charAt(i + 1) == delimiter)
                    sb.append(delimiter);
                else if (str.charAt(i + 1) >= '0' && str.charAt(i + 1) <= '9') {
                    // found an octal value
                    int a2 = str.charAt(i + 1) - '0';
                    int a1 = str.charAt(i + 2) - '0';
                    if (a1 < 0 || a1 > 9)
                        throw new IllegalArgumentException("Malformed octal value in string:" + str);
                    int a0 = str.charAt(i + 3) - '0';
                    if (a0 < 0 || a0 > 9)
                        throw new IllegalArgumentException("Malformed octal value in string:" + str);
                    int decimal = a2 * 8 * 8 + a1 * 8 + a0;
                    sb.append((char) decimal);
                    i++; i++;
                }
                i++;
            } else
                sb.append(str.charAt(i));
        }

        return sb.toString();
    }

    /**
     * Takes the internal representation of a string, and creates the textual representation
     * that is ready to be printed.
     * It adds double quote at the beginning and end, and transforms special characters into
     * the textual representation (ex: newline becomes "\n").
     */
    public static String enquoteCString(String value) {
        return enquoteCString(value, '"');
    }
    public static String enquoteCString(String value, char delimiter) {
        final int length = value.length();
        StringBuilder result = new StringBuilder();
        result.append(delimiter);
        for (int offset = 0, codepoint; offset < length; offset += Character.charCount(codepoint)) {
            codepoint = value.codePointAt(offset);
            if (codepoint > 0xFF) {
                result.appendCodePoint(codepoint);
            } else if (codepoint == delimiter) {
                result.append("\\" + delimiter);
            } else if (codepoint == '\\') {
                result.append("\\\\");
            } else if (codepoint == '\n') {
                result.append("\\n");
            } else if (codepoint == '\t') {
                result.append("\\t");
            } else if (codepoint == '\r') {
                result.append("\\r");
            } else if (codepoint == '\f') {
                result.append("\\f");
            } else if (codepoint >= 32 && codepoint < 127) {
                result.append((char)codepoint);
            } else if (codepoint <= 0xff) {
                result.append("\\");
                result.append(String.format("%03o", codepoint));
            }
        }
        result.append(delimiter);
        return result.toString();
    }

    public static void throwIfSurrogatePair(int codePoint) {
        if (codePoint >= 0xd800 && codePoint <= 0xdfff) {
            //we are trying to encode a surrogate pair, which the unicode
            //standard forbids
            throw new IllegalArgumentException(Integer.toHexString(codePoint) +
                    " is not in the accepted unicode range.");
        }
        if (codePoint >= 0x110000)
            throw new IllegalArgumentException(Integer.toHexString(codePoint) +
                    " is not in the accepted unicode range.");
    }

    public static int lastIndexOfAny(String str, String search, int offset) {
        if (str.equals("") || search.equals("")) {
            return -1;
        }
        for (int i = str.length(), strCodepoint; i > 0; i -= Character.charCount(strCodepoint)) {
            strCodepoint = str.codePointBefore(i);
            for (int j = search.length(), searchCodepoint; j > 0; j -= Character.charCount(searchCodepoint)) {
                searchCodepoint = search.codePointBefore(j);
                if (strCodepoint == searchCodepoint) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static int indexOfAny(String str, String search, int offset) {
        if (str.equals("") || search.equals("")) {
            return -1;
        }
        for (int i = 0, strCodepoint; i < str.length(); i += Character.charCount(strCodepoint)) {
            strCodepoint = str.codePointAt(i);
            for (int j = 0, searchCodepoint; j < search.length(); j += Character.charCount(searchCodepoint)) {
                searchCodepoint = search.codePointAt(j);
                if (strCodepoint == searchCodepoint) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Removes the first and last double-quote characters and unescapes special characters
     * that start with backslash: newline, carriage return, line feed, tab and backslash.
     * Characters between 127 and 255 are stored as \xFF
     * Characters between 256 and 65535 are stored as \uFFFF
     * Characters above 65536 are stored as \u0010FFFF
     * @param str Python like double-quoted string
     * @return unescaped and unquoted string
     */
    public static String unquoteKString(String str) {
        StringBuilder sb = new StringBuilder();
        if (str.charAt(0) != '"') {
            throw new IllegalArgumentException("Expected to find double quote at the beginning of string: " + str);
        }
        if (str.charAt(str.length() - 1) != '"') {
            throw new IllegalArgumentException("Expected to find double quote at the end of string: " + str);
        }
        for (int i = 1; i < str.length() - 1; i++) {
            if (str.charAt(i) == '\\') {
                if (str.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else if (str.charAt(i + 1) == '\\') {
                    sb.append('\\');
                    i++;
                } else if (str.charAt(i + 1) == 'n') {
                    sb.append('\n');
                    i++;
                } else if (str.charAt(i + 1) == 'r') {
                    sb.append('\r');
                    i++;
                } else if (str.charAt(i + 1) == 't') {
                    sb.append('\t');
                    i++;
                } else if (str.charAt(i + 1) == 'f') {
                    sb.append('\f');
                    i++;
                } else if (str.charAt(i + 1) == 'x') {
                    String arg = str.substring(i + 2, i + 4);
                    sb.append((char)Integer.parseInt(arg, 16));
                    i += 3;
                } else if (str.charAt(i + 1) == 'u') {
                    String arg = str.substring(i + 2, i + 6);
                    int codePoint = Integer.parseInt(arg, 16);
                    StringUtil.throwIfSurrogatePair(codePoint);
                    sb.append((char)codePoint);
                    i += 5;
                } else if (str.charAt(i + 1) == 'U') {
                    String arg = str.substring(i + 2, i + 10);
                    int codePoint = Integer.parseInt(arg, 16);
                    StringUtil.throwIfSurrogatePair(codePoint);
                    sb.append(Character.toChars(codePoint));
                    i += 9;
                }
            } else {
                sb.append(str.charAt(i));
            }
        }
        return sb.toString();
    }

    /**
     * Adds double-quote at the beginning and end of the string and escapes special characters
     * with backslash: newline, carriage return, line feed, tab and backslash.
     * Characters between 127 and 255 are stored as \xFF
     * Characters between 256 and 65535 are stored as \uFFFF
     * Characters above 65536 are stored as \u0010FFFF
     * @param value any string
     * @return Python like textual representation of the string
     */
    public static String enquoteKString(String value) {
        final int length = value.length();
        StringBuilder result = new StringBuilder();
        result.append("\"");
        for (int offset = 0, codepoint; offset < length; offset += Character.charCount(codepoint)) {
            codepoint = value.codePointAt(offset);
            if (codepoint == '"') {
                result.append("\\\"");
            } else if (codepoint == '\\') {
                result.append("\\\\");
            } else if (codepoint == '\n') {
                result.append("\\n");
            } else if (codepoint == '\t') {
                result.append("\\t");
            } else if (codepoint == '\r') {
                result.append("\\r");
            } else if (codepoint == '\f') {
                result.append("\\f");
            } else if (codepoint >= 32 && codepoint < 127) {
                result.append((char)codepoint);
            } else if (codepoint <= 0xff) {
                result.append("\\x");
                result.append(String.format("%02x", codepoint));
            } else if (codepoint <= 0xffff) {
                result.append("\\u");
                result.append(String.format("%04x", codepoint));
            } else {
                result.append("\\U");
                result.append(String.format("%08x", codepoint));
            }
        }
        result.append("\"");
        return result.toString();
    }

    /**
     * Returns the two-letter code for a general category of Unicode code point.
     */
    public static String getCategoryCode(byte cat) {
        switch(cat) {
            case Character.COMBINING_SPACING_MARK:
                return "Mc";
            case Character.CONNECTOR_PUNCTUATION:
                return "Pc";
            case Character.CONTROL:
                return "Cc";
            case Character.CURRENCY_SYMBOL:
                return "Sc";
            case Character.DASH_PUNCTUATION:
                return "Pd";
            case Character.DECIMAL_DIGIT_NUMBER:
                return "Nd";
            case Character.ENCLOSING_MARK:
                return "Me";
            case Character.END_PUNCTUATION:
                return "Pe";
            case Character.FINAL_QUOTE_PUNCTUATION:
                return "Pf";
            case Character.FORMAT:
                return "Cf";
            case Character.INITIAL_QUOTE_PUNCTUATION:
                return "Pi";
            case Character.LETTER_NUMBER:
                return "Nl";
            case Character.LINE_SEPARATOR:
                return "Zl";
            case Character.LOWERCASE_LETTER:
                return "Ll";
            case Character.MATH_SYMBOL:
                return "Sm";
            case Character.MODIFIER_LETTER:
                return "Lm";
            case Character.MODIFIER_SYMBOL:
                return "Sk";
            case Character.NON_SPACING_MARK:
                return "Mn";
            case Character.OTHER_LETTER:
                return "Lo";
            case Character.OTHER_NUMBER:
                return "No";
            case Character.OTHER_PUNCTUATION:
                return "Po";
            case Character.OTHER_SYMBOL:
                return "So";
            case Character.PARAGRAPH_SEPARATOR:
                return "Zp";
            case Character.PRIVATE_USE:
                return "Co";
            case Character.SPACE_SEPARATOR:
                return "Zs";
            case Character.START_PUNCTUATION:
                return "Ps";
            case Character.SURROGATE:
                return "Cs";
            case Character.TITLECASE_LETTER:
                return "Lt";
            case Character.UNASSIGNED:
                return "Cn";
            case Character.UPPERCASE_LETTER:
                return "Lu";
            default:
                assert false: "should be exhaustive list of categories";
                return null; //unreachable
        }
    }

    public static String getDirectionalityCode(byte cat) {
        switch(cat) {
            case Character.DIRECTIONALITY_ARABIC_NUMBER:
                return "AN";
            case Character.DIRECTIONALITY_BOUNDARY_NEUTRAL:
                return "BN";
            case Character.DIRECTIONALITY_COMMON_NUMBER_SEPARATOR:
                return "CS";
            case Character.DIRECTIONALITY_EUROPEAN_NUMBER:
                return "EN";
            case Character.DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR:
                return "ES";
            case Character.DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR:
                return "ET";
            case Character.DIRECTIONALITY_LEFT_TO_RIGHT:
                return "L";
            case Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING:
                return "LRE";
            case Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE:
                return "LRO";
            case Character.DIRECTIONALITY_NONSPACING_MARK:
                return "NSM";
            case Character.DIRECTIONALITY_OTHER_NEUTRALS:
                return "ON";
            case Character.DIRECTIONALITY_PARAGRAPH_SEPARATOR:
                return "B";
            case Character.DIRECTIONALITY_POP_DIRECTIONAL_FORMAT:
                return "PDF";
            case Character.DIRECTIONALITY_RIGHT_TO_LEFT:
                return "R";
            case Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
                return "AL";
            case Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING:
                return "RLE";
            case Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE:
                return "RLO";
            case Character.DIRECTIONALITY_SEGMENT_SEPARATOR:
                return "S";
            case Character.DIRECTIONALITY_UNDEFINED:
                throw new IllegalArgumentException();
            case Character.DIRECTIONALITY_WHITESPACE:
                return "WS";
            default:
                assert false: "should be exhaustive list of directionalities";
                return null; //unreachable
        }
    }

    /**
     * split string to lines in a way that no lines will exceed 80 columns
     * NOTE: strings split only at whitespace character ' ', if string contains no ' ', it's returned as is
     * @param str string to split
     * @return new string with newlines added
     */
    public static String splitLines(String str) {
        return splitLines(str, 80);
    }

    /**
     * split string to lines in a way that no lines will exceed `col` columns
     * NOTE: strings split only at whitespace character ' ', if string contains no ' ', it's returned as is
     * @param str string to split
     * @param col rightmost column
     * @return new string with newlines added
     */
    public static String splitLines(String str, final int col) {
        String[] lines = str.split("\n");
        StringBuilder builder = new StringBuilder();
        String nl = "";
        for (String line : lines) {
            builder.append(nl);
            if (line.length() < col) {
                builder.append(line);
            } else {
                builder.append(splitLine(line, col));
            }
            nl = "\n";
        }
        return builder.toString();
    }

    private static String splitLine(String str, final int col) {
        if (str.length() < col) {
            return str;
        }

        // scan from `col` to left
        for (int i = col - 1; i > 0; i--) {
            if (str.charAt(i) == ' ') {
                return str.substring(0, i) + "\n" + splitLine(str.substring(i + 1), col);
            }
        }

        // we reached to beginning of the string and it contains no whitespaces before the `col`
        // but it's longer than `col` so we should replace first space after rightmost column
        // with a newline to make it shorter
        for (int i = col; i < str.length(); i++) {
            if (str.charAt(i) == ' ') {
                return str.substring(0, i) + "\n" + splitLine(str.substring(i + 1), col);
            }
        }

        // string has no spaces to split
        return str;
    }

    /**
     * Finesse the JCommander usage output to make it more readable to the user.
     *
     * This function does two things. First, it reworks the indentation to fix a
     * bug where different commands are indented differently. Second, it
     * separates out experimental and non-experimental options in order to print
     * their usage separately.
     * @param string The unfiltered output from JCommander's usage
     * @return An array of strings. If the command has experimental options, they
     * are in the second string, and the main options are in the first string.
     * Otherwise, there will only be one string outputted.
     */
    public static String[] finesseJCommanderUsage(String string, JCommander jc) {
        //for some reason the usage pattern indents commands inconsistently, so we need to adjust it
        string = string.replaceAll("        ", "    ");
        String lastLine = "";
        StringBuilder mainOptions = new StringBuilder();
        StringBuilder experimentalOptions = new StringBuilder();
        experimentalOptions.append("  Experimental Options:\n");
        boolean inExperimentalOptions = false;
        for (String line : string.split("\n")) {
            if (line.startsWith("    --")) {
                if (lastLine.compareTo(line) > 0) {
                    inExperimentalOptions = true;
                }
                lastLine = line;
            }
            if (inExperimentalOptions) {
                experimentalOptions.append(line);
                experimentalOptions.append("\n");
            } else {
                mainOptions.append(line);
                mainOptions.append("\n");
            }
        }
        return new String[] {mainOptions.toString(), experimentalOptions.toString()};
    }

    /**
     * Takes a textual representation of a KLabel using backticks to delimit
     * and returns the string representation of the KLabel that it corresponds to
     *
     * Used by the KAST parser.
     *
     * @param str An image of a parser token corresponding to a KLabel in KORE which
     * begins and ends with backtick
     * @return The string value of the KLabel
     */
    public static String unescapeKoreKLabel(String str) {
        char delimiter = '`';
        StringBuilder sb = new StringBuilder();
        if (str.charAt(0) != delimiter) {
            throw new IllegalArgumentException("Expected to find " + delimiter + " at the beginning of string: " + str);
        }
        if (str.charAt(str.length() - 1) != delimiter) {
            throw new IllegalArgumentException("Expected to find " + delimiter + " at the end of string: " + str);
        }
        for (int i = 1; i < str.length() - 1; i++) {
            if (str.charAt(i) == 0x7F || str.charAt(i) < 32)
                throw new IllegalArgumentException("Special characters not supported here:" + str);
            if (str.charAt(i) == '\\') {
                if (str.charAt(i + 1) == '\\')
                    sb.append('\\');
                else if (str.charAt(i + 1) == delimiter)
                    sb.append(delimiter);
                i++;
            } else
                sb.append(str.charAt(i));
        }

        return sb.toString();
    }

    /**
     * Takes the value of a KLabel and returns a string representation, delimited with
     * backticks, of the syntax of that KLabel in KORE.
     *
     * Used by the KAST pretty printer.
     *
     * @param str A string value corresponding to a KLabel.
     * @return A string which can be parsed back by a KORE parser to reach the original KLabel.
     */
    public static String escapeKoreKLabel(String value) {
        char delimiter = '`';
        final int length = value.length();
        StringBuilder result = new StringBuilder();
        result.append(delimiter);
        for (int offset = 0, codepoint; offset < length; offset += Character.charCount(codepoint)) {
            codepoint = value.codePointAt(offset);
            if (codepoint == 0x7F || codepoint < 32) {
                throw new IllegalArgumentException("Special characters not supported here:" + value);
            } else if (codepoint == delimiter) {
                result.append("\\" + delimiter);
            } else if (codepoint == '\\') {
                result.append("\\\\");
            } else {
                result.appendCodePoint(codepoint);
            }
        }
        result.append(delimiter);
        return result.toString();
    }

    public static String[] asciiReadableEncodingDefault = new String[] {
            null,// 00
            null,// 01
            null,// 02
            null,// 03
            null,// 04
            null,// 05
            null,// 06
            null,// 07
            null,// 08
            null,// 09
            null,// 0a
            null,// 0b
            null,// 0c
            null,// 0d
            null,// 0e
            null,// 0f
            null,// 10
            null,// 11
            null,// 12
            null,// 13
            null,// 14
            null,// 15
            null,// 16
            null,// 17
            null,// 18
            null,// 19
            null,// 1a
            null,// 1b
            null,// 1c
            null,// 1d
            null,// 1e
            null,// 1f
            "Spce",// 20
            "Bang",// 21
            "Quot",// 22
            "Hash",// 23
            "Dolr",// 24
            "Perc",// 25
            "And",// 26
            "Apos",// 27
            "LPar",// 28
            "RPar",// 29
            "Star",// 2a
            "Plus",// 2b
            "Comm",// 2c
            "Hyph",// 2d
            "Stop",// 2e
            "Slsh",// 2f
            "0",// 30
            "1",// 31
            "2",// 32
            "3",// 33
            "4",// 34
            "5",// 35
            "6",// 36
            "7",// 37
            "8",// 38
            "9",// 39
            "Coln",// 3a
            "SCln",// 3b
            "LT",// 3c
            "Eqls",// 3d
            "GT",// 3e
            "Ques",// 3f
            "AT",// 40
            "A",// 41
            "B",// 42
            "C",// 43
            "D",// 44
            "E",// 45
            "F",// 46
            "G",// 47
            "H",// 48
            "I",// 49
            "J",// 4a
            "K",// 4b
            "L",// 4c
            "M",// 4d
            "N",// 4e
            "O",// 4f
            "P",// 50
            "Q",// 51
            "R",// 52
            "S",// 53
            "T",// 54
            "U",// 55
            "V",// 56
            "W",// 57
            "X",// 58
            "Y",// 59
            "Z",// 5a
            "LSqB",// 5b
            "Bash",// 5c
            "RSqB",// 5d
            "Xor",// 5e
            "Unds",// 5f
            "BQuo",// 60
            "a",// 61
            "b",// 62
            "c",// 63
            "d",// 64
            "e",// 65
            "f",// 66
            "g",// 67
            "h",// 68
            "i",// 69
            "j",// 6a
            "k",// 6b
            "l",// 6c
            "m",// 6d
            "n",// 6e
            "o",// 6f
            "p",// 70
            "q",// 71
            "r",// 72
            "s",// 73
            "t",// 74
            "u",// 75
            "v",// 76
            "w",// 77
            "x",// 78
            "y",// 79
            "z",// 7a
            "LBra",// 7b
            "Pipe",// 7c
            "RBra",// 7d
            "Tild",// 7e
            null// 7f
    };

    public static void encodeStringToAlphanumeric(StringBuilder sb, String name, String[] asciiReadableEncodingTable, Pattern identChar, String escapeChar) {
        boolean inIdent = true;
        for (int i = 0; i < name.length(); i++) {
            if (identChar.matcher(name).region(i, name.length()).lookingAt()) {
                if (!inIdent) {
                    inIdent = true;
                    sb.append(escapeChar);
                }
                sb.append(name.charAt(i));
            } else {
                if (inIdent) {
                    inIdent = false;
                    sb.append(escapeChar);
                }
                int charAt = (int) name.charAt(i);
                if (charAt < 128 && asciiReadableEncodingTable[charAt] != null) {
                    sb.append(asciiReadableEncodingTable[charAt]);
                } else {
                    sb.append(String.format("%04x", charAt));
                }
            }
        }
        if (!inIdent) {
            sb.append(escapeChar);
        }
    }

    public static String decodeKoreString(String encoded) {
        for (int i = 0; i < asciiReadableEncodingDefault.length; i++) {
            String encoding = asciiReadableEncodingDefault[i];
            if (encoding == null) continue;
            encoding = "'" + encoding + "'";
            encoded = encoded.replaceAll(encoding, String.valueOf((char) i));
        }
        return encoded;
    }

    public static String[] splitOneDimensionalAtt(String att) {
        String[] splitted = att.trim().split(",");
        for (int i = 0; i < splitted.length; i++) {
            splitted[i] = splitted[i].trim();
        }
        return splitted;
    }

    public static String[][] splitTwoDimensionalAtt(String att) {
        String[] parts = att.trim().split(";");
        String[][] splitted = new String[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            String[] subparts = splitOneDimensionalAtt(parts[i]);
            splitted[i] = subparts;
        }
        return splitted;
    }
}
