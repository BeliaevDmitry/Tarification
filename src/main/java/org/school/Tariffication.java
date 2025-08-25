package org.school;

import org.school.personalLoad.controller.TarifficationController;

public class Tariffication {
    public static void main(String[] args) {
        String inputPath = "C:\\Users\\dimah\\Desktop\\1 полугодие нагрузка 2025-2026.xlsx";
        //String inputPath = "C:\\Users\\dimah\\Desktop\\1.xlsx";
        String outputPath = "C:\\Users\\dimah\\Desktop\\report.xlsx";

        TarifficationController controller = new TarifficationController();
        controller.processTariffication(inputPath, outputPath);
    }
}