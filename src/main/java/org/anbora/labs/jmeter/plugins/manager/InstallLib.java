package org.anbora.labs.jmeter.plugins.manager;

import java.io.File;

public record InstallLib(File moveFile, File installFile,
                         File restartFile) {
}
