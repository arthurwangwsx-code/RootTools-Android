package com.pcapdroid.mitm;

import java.io.Serializable;

/**
 * Wire-compatible public contract for the PCAPdroid MITM add-on service.
 * This is intentionally limited to constants and the Serializable config object.
 */
public final class MitmAPI {
    private MitmAPI() {}

    public static final String PACKAGE_NAME = "com.pcapdroid.mitm";
    public static final String MITM_SERVICE = PACKAGE_NAME + ".MitmService";
    public static final int MSG_ERROR = -1;
    public static final int MSG_START_MITM = 1;
    public static final int MSG_GET_CA_CERTIFICATE = 2;
    public static final int MSG_STOP_MITM = 3;
    public static final int MSG_DISABLE_DOZE = 4;
    public static final String MITM_CONFIG = "mitm_config";
    public static final String CERTIFICATE_RESULT = "certificate";

    public static final class MitmConfig implements Serializable {
        public int proxyPort;
        public boolean transparentMode;
        public boolean sslInsecure;
        public boolean dumpMasterSecrets;
        public boolean shortPayload;
        public String proxyAuth;
        public String additionalOptions;
    }
}
