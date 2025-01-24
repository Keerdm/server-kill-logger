package com.keerdm.server_kill_logger;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

@Mod("server_kill_logger")
public class Server_Kill_Logger {
    public Server_Kill_Logger() {
        MinecraftForge.EVENT_BUS.register(this);
    }
}