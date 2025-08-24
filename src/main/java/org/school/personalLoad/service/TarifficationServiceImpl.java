package org.school.personalLoad.service;

import org.apache.poi.ss.usermodel.Sheet;
import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationPerson;

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
