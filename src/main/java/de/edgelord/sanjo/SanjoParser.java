package de.edgelord.sanjo;

import java.io.IOException;
import java.util.*;

/**
 * A utility class to parse
 * a {@link SanjoFile}.
 */
public class SanjoParser {

    public static final String CLASS_PREFIX = ":";
    public static final String KEY_PREFIX = ".";
    public static final String ASSIGNMENT_OPERATOR = "=";
    public static final String DEFAULT_LIST_KEY_SUFFIX = "[]";
    public static final String DEFAULT_LIST_SEPARATOR = ",";
    public static final int DEFAULT_INDENTION_WIDTH = 4;

    public static final String INDENTATION_WIDTH_KEY = "indentation";
    public static final String NEWLINE_KEY = "newline";
    public static final String AUTHOR_KEY = "author";
    public static final String NAME_KEY = "name";
    public static final String LIST_KEY_SUFFIX_KEY = "list_suffix";
    public static final String LIST_SEPARATOR = "list_separator";
    public static final char SPACE = ' ';

    private final SanjoFile file;
    private final MetaInf metaInf;
    private int lastIndentLevel = 0;
    private SJClass defaultClass = SJClass.defaultClass();
    private Map<Integer, SJClass> workingClasses = new HashMap<>();

    public SanjoParser(final SanjoFile file) {
        this.file = file;
        metaInf = new MetaInf(DEFAULT_INDENTION_WIDTH, DEFAULT_LIST_KEY_SUFFIX, DEFAULT_LIST_SEPARATOR);
    }

    public SJClass parse() throws IOException {
        final List<String> lines = file.readLines();
        workingClasses.put(0, defaultClass);
        int lineNumber = 1;
        for (final String rawLine : lines) {
            String line = removeLeadingSpaces(rawLine);
            int currentIndent = rawLine.length() - line.length();
            int currentIndentLevel = currentIndent / metaInf.indentionWidth + 1;

            if(line.startsWith(CLASS_PREFIX)) {
                // class definition
                checkIndention(currentIndent, lineNumber);
                if (currentIndentLevel > lastIndentLevel) {
                    throw indentionError(lineNumber);
                }
                final SJClass newClass = new SJClass(line.replaceFirst(CLASS_PREFIX, ""));
                if (currentIndentLevel == lastIndentLevel) {
                    // case 1: current indent is equal to the last indent -
                    // new class should be a direct subclass of the current class'
                    // parent, or, in case the indent is 0, a direct child of the
                    // default class
                    final SJClass parent = currentIndentLevel == 0 ? defaultClass : workingClasses.get(currentIndentLevel - 1);
                    newClass.parentClass = parent;
                    parent.getChildren().add(newClass);
                } else if (currentIndentLevel < lastIndentLevel) {
                    // case 2: current indent is smaller than the last indent
                    // new class should be a subclass of some parent of some parent
                    // of the current class, depending on the indention delta
                    final int delta = lastIndentLevel - currentIndentLevel;
                    SJClass parent = workingClasses.get(currentIndentLevel - 1);
                    parent.getChildren().add(newClass);
                    newClass.parentClass = parent;
                } else {
                    // you cannot indent a class definition
                    throw indentionError(lineNumber);
                }
                workingClasses.put(currentIndentLevel, newClass);
                // increment current indent
                // because any following classes or
                // k-v pairs have to be indented
                // - allow for empty classes?
                currentIndentLevel++;
            } else if (line.startsWith(KEY_PREFIX)) {
                // key-value pair definition
                checkIndention(currentIndent, lineNumber);
                // TODO: check class affiliation by indention-level
                final SJValue value = createValue(line, lineNumber);
                workingClasses.get(currentIndentLevel - 1).getValues().put(value.getKey(), value);
            } else {
                // everything else is ignored
                // as a comment
                continue;
            }
            lineNumber++;
            lastIndentLevel = currentIndentLevel;
        }
        return defaultClass;
    }

    private SJValue createValue(final String snippet, final int lineNumber) {
        final String[] keyAndValue = snippet.split(ASSIGNMENT_OPERATOR, 2);
        String keyString = keyAndValue[0];
        final String valueString = keyAndValue[1];
        Object value = null;
        if (keyString.endsWith(metaInf.listSuffix)) {
            value = new ArrayList<>(Arrays.asList(valueString.split(metaInf.listSeparator)));
            keyString = keyString.substring(0, keyString.length() - metaInf.listSuffix.length());
        } else {
            value = valueString;
        }

        return new SJValue(keyString.replaceFirst(KEY_PREFIX, ""), value);
    }

    private boolean isIndentionLegal(final int indention) {
        return indention % metaInf.indentionWidth == 0;
    }

    private void checkIndention(final int indention, final int lineNumber) {
        if(!isIndentionLegal(indention)) {
            throw indentionError(lineNumber);
        }
    }

    /**
     * Removes the leading from the given
     * String.
     *
     * @return the given String but without leading spaces
     */
    private String removeLeadingSpaces(final String s) {
        int spaceCount = 0;
        for (final char c : s.toCharArray()) {
            if (c == SPACE) {
                spaceCount++;
            } else {
                break;
            }
        }

        return s.substring(spaceCount);
    }

    private SanjoParserError indentionError(final int lineNumber) {
        return new SanjoParserError(SanjoParserError.INDENTION_ERROR_MESSAGE, lineNumber);
    }

    public SanjoFile getFile() {
        return file;
    }

    public MetaInf getMetaInf() {
        return metaInf;
    }

    public class SanjoParserError extends RuntimeException {
        private static final String INDENTION_ERROR_MESSAGE = "Illegal indention";
        public SanjoParserError(final String message, final int lineNumber) {
            super("\n    Error parsing file " + file.getAbsolutePath() + ": "
                    + message + " in line " + lineNumber);
        }
    }
}
