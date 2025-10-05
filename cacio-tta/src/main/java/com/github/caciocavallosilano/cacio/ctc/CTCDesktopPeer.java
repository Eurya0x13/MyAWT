package com.github.caciocavallosilano.cacio.ctc;

import java.awt.peer.DesktopPeer;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.awt.Desktop.Action;
import java.awt.desktop.AboutHandler;
import java.awt.desktop.QuitHandler;

public class CTCDesktopPeer implements DesktopPeer {
    public boolean isSupported(Action action) {
        switch(action) {
            case APP_QUIT_HANDLER:
            case APP_ABOUT:
                return true;
            default:
                return false;
        }
    }
    public void setAboutHandler(final AboutHandler handler) {
        // Stub
    }
    public void setQuitHandler(final QuitHandler handler) {
        // Stub
    }

  public void open(File file) throws IOException {
    //openFile(file.getAbsolutePath());
  }
  public void edit(File file) throws IOException {
    throw new IOException("Action not supported");
  }
  public void print(File file) throws IOException {
    throw new IOException("Action not supported");
  }
  public void mail(URI mailtoURL) throws IOException {
    throw new IOException("Action not supported");
  }
  public void browse(URI uri) throws IOException {
    //openUri(uri.toString());
  }
}
