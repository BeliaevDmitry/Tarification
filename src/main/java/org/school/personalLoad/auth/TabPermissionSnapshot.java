package org.school.personalLoad.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TabPermissionSnapshot implements Serializable {
    private AppTab tab;
    private boolean canView;
    private boolean canEdit;

    public String getTabDisplayName() {
        return tab != null ? tab.getDisplayName() : "";
    }
}
