package org.example.service;

import org.apache.poi.ss.usermodel.Sheet;
import org.example.model.SubjectWithGroup;
import org.example.model.TarifficationPerson;

import java.util.List;

public class TarifficationServiceImpl implements TarifficationService {
    @Override
    public List<TarifficationPerson> analyzeSheet(Sheet sheet) {
        return List.of();
    }

    @Override
    public void createReport(List<TarifficationPerson> tarifficationList, List<SubjectWithGroup> subjectWithGroupList, String outputPath) {

    }

    @Override
    public List<TarifficationPerson> addingGroup(List<TarifficationPerson> list, List<SubjectWithGroup> groupList) {
        return List.of();
    }

    @Override
    public List<SubjectWithGroup> searchGroup(Sheet sheet) {
        return List.of();
    }
}
