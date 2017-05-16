package com.amungo.andronut;

class DeviceCommands {
    static final int CMD_FW_LOAD         = 0xA0;

    static final int CMD_GET_VERSION     = 0xB0;
    static final int CMD_INIT_PROJECT    = 0xB1;
    static final int CMD_REG_WRITE       = 0xB3;
    static final int CMD_READ_DEBUG_INFO = 0xB4;
    static final int CMD_REG_READ        = 0xB5;
    static final int CMD_CYPRESS_RESET   = 0xBF;
}
