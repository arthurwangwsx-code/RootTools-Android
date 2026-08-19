package com.arthur.roottools.privilege;

interface IPrivilegeUserService {
    int getBackendUid();
    boolean packageExists(String packageName);
    String getPackageEnabledState(String packageName);
    boolean isPackageRunning(String packageName);
    boolean forceStopPackage(String packageName);
    boolean setPackageEnabled(String packageName, boolean enabled);
    String getStandbyBucket(String packageName);
    boolean setStandbyBucket(String packageName, int bucket);
    String getAppOp(String packageName, String opName);
    boolean setAppOp(String packageName, String opName, String mode);
    boolean setBackgroundAllowed(String packageName, boolean allowed);
    boolean setComponentEnabled(String componentName, boolean enabled);
    String getTopPackage();
    boolean setAppiumTestMode(boolean enabled);
    String frameworkSelfTest(String ownPackageName);
}
