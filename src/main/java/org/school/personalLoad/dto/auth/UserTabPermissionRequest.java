package org.school.personalLoad.dto.auth;

import lombok.Data;
import org.school.personalLoad.auth.AppTab;

@Data
public class UserTabPermissionRequest {
    private AppTab tab;
    private Boolean canView;
    private Boolean canEdit;
    private Boolean canImport;
    private Boolean canExport;
}
