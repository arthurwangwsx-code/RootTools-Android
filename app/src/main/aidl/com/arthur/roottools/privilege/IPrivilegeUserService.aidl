package com.arthur.roottools.privilege;

interface IPrivilegeUserService {
    int getBackendUid();
    boolean packageExists(String packageName);
    String getPackageEnabledState(String packageName);
    boolean isPackageRunning(String packageName);
    boolean forceStopPackage(String packageName);
    boolean setPackageEnabled(String packageName, boolean enabled);
    boolean setComponentEnabled(String componentName, boolean enabled);
    boolean launchActivity(String componentName);
    String getStandbyBucket(String packageName);
    boolean setStandbyBucket(String packageName, int bucket);
    String getAppOp(String packageName, String opName);
    boolean setAppOp(String packageName, String opName, String mode);
    boolean setRuntimePermission(String packageName, String permissionName, boolean granted);
    boolean setBackgroundAllowed(String packageName, boolean allowed);
    boolean setAppiumTestMode(boolean enabled);
    String getTopPackage();
    String appRuntimeSnapshot(String packageName);
    String frameworkSelfTest(String ownPackageName);
}
