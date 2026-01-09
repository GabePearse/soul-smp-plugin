package me.Evil.soulSMP.anvil;

public class SoulAnvilSession {

    private String rename = ""; // per-session

    public String getRename() {
        return rename;
    }

    public void setRename(String rename) {
        this.rename = (rename == null ? "" : rename);
    }
}
