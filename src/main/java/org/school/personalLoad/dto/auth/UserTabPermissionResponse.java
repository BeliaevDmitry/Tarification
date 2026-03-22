package org.school.personalLoad.dto.auth;

import lombok.Builder;
import lombok.Value;
import org.school.personalLoad.auth.AppTab;

@Value
@Builder
public class UserTabPermissionResponse {
    AppTab tab;
    String tabDisplayName;
    boolean canView;
    boolean canEdit;
}
