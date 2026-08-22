# RootTools starts this entry point through root app_process. Keep the class name and main method
# stable in release builds because it is referenced by a fixed privileged command, not by Android
# manifest/resource reachability.
-keep class com.arthur.roottools.privilege.shadow.ShadowDisplayDaemon {
    public static void main(java.lang.String[]);
}
# Personal root toolbox: keep rules intentionally empty for now.
