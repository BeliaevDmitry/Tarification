package org.example.service;

import org.apache.poi.ss.usermodel.Sheet;
import org.example.model.SubjectWithGroup;
import org.example.model.TarifficationPerson;

import java.util.List;

public interface TarifficationService {

    List<TarifficationPerson> analyzeSheet(Sheet sheet);

    void createReport(List<TarifficationPerson> tarifficationList,
                      List<SubjectWithGroup> subjectWithGroupList,
                      String outputPath);

    List<TarifficationPerson> addingGroup(List<TarifficationPerson> list, List<SubjectWithGroup> groupList);

    List<SubjectWithGroup> searchGroup (Sheet sheet);


}
