package net.sberg.openkim.user;

import lombok.Data;

@Data
public class UserChangePwd {
    private String username;
    private String oldPwd;
    private String newPwd;
}
