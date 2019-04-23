package com.criteo.publisher.network;

import com.criteo.publisher.model.Cdb;
import com.criteo.publisher.model.Config;

public class NetworkResult {
    private Cdb cdb;
    private Config config;

    NetworkResult(Cdb cdb, Config config) {
        this.cdb = cdb;
        this.config = config;
    }

    NetworkResult() {
    }

    public Cdb getCdb() {
        return cdb;
    }

    public void setCdb(Cdb cdb) {
        this.cdb = cdb;
    }

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }
}