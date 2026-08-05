package org.school.personalLoad.service;

import org.school.personalLoad.dto.contingent.IupOrderDocumentDtos;

public interface IupOrderDocumentService {

    GeneratedDocument generate(String academicYear, IupOrderDocumentDtos.GenerateRequest request);

    record GeneratedDocument(byte[] content, String fileName) {
    }
}
