package org.school.educationalwork.parser;

import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class DocxCells {
    private DocxCells() {}

    static String cell(XWPFTable table, int row, int column) {
        if (table == null || row >= table.getNumberOfRows() || column >= table.getRow(row).getTableCells().size()) {
            return "";
        }
        return clean(table.getRow(row).getCell(column).getText());
    }

    static String clean(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    static boolean emptyRow(XWPFTable table, int row) {
        return table.getRow(row).getTableCells().stream().map(XWPFTableCell::getText).map(DocxCells::clean).allMatch(String::isBlank);
    }

    static List<String> people(String value) {
        if (value == null || value.isBlank() || value.trim().equals("-")) return List.of();
        return Arrays.stream(value.split("[;\\n,]+"))
                .map(DocxCells::clean)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }
}
