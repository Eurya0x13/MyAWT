/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.github.caciocavallosilano.cacio.ctc;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

/**
 *
 * @author maks
 */
public class CTCClipboard extends Clipboard {
    public static final CTCClipboard INSTANCE = new CTCClipboard();
    private static final Object sSyncLock = new Object();
    private static long lastClipboardCheckTime;
    private CTCClipboard() {
        super("Android JNI clipboard (text-only)");
    }
    
    
    
    @Override
    public DataFlavor[] getAvailableDataFlavors() {
        querySystemClipboard();
        return super.getAvailableDataFlavors();
    }
    
    @Override
    public Transferable getContents(Object requestor) {
        querySystemClipboard();
        return super.getContents(requestor);
    }
    
    @Override
    public Object getData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        querySystemClipboard();
        return super.getData(flavor);
    }
    
    @Override
    public boolean isDataFlavorAvailable(DataFlavor flavor) {
        querySystemClipboard();
        return super.isDataFlavorAvailable(flavor);
    }
    
    @Override
    public void setContents(Transferable contents, ClipboardOwner owner) {
        super.setContents(contents, owner); // run the clipboard lifecycle
        transferTextToOS(contents);
    }
    
    private static void querySystemClipboard() {
        
        try {
            synchronized(sSyncLock) {
                if(lastClipboardCheckTime + 300 > System.currentTimeMillis()) return;
                nQuerySystemClipboard();
                sSyncLock.wait();
            }
        }catch(InterruptedException e) {
           throw new IllegalStateException();
        }
    }
    
    @SuppressWarnings("unused")
    private static void systemClipboardDataReceived(String clipboardData, String clipboardDataMime) {
        synchronized(sSyncLock) {
            lastClipboardCheckTime = System.currentTimeMillis();
            if(clipboardData != null && clipboardDataMime != null)
                INSTANCE.contents = new CTCStringTransferable(clipboardData, clipboardDataMime);
            sSyncLock.notifyAll();
        }
    }
    
    private static void transferTextToOS(Transferable transferable) {
        for(DataFlavor flavor : transferable.getTransferDataFlavors()) {
            if(!flavor.isFlavorTextType()) continue;
            String mimeType = flavor.getSubType().equals("html") ? "text/html" : "text/plain";
            try {
                if(CharSequence.class.isAssignableFrom(flavor.getRepresentationClass())) {
                    nPutClipboardData(transferable.getTransferData(flavor).toString(), mimeType);
                    return;
                }else if(flavor.isRepresentationClassCharBuffer()) {
                    CharBuffer clipData = (CharBuffer) transferable.getTransferData(flavor);
                    nPutClipboardData(new String(clipData.array()), mimeType);
                    return;
                }else if(flavor.isRepresentationClassReader()) {
                    try (Reader inputStream = (Reader) transferable.getTransferData(flavor)) {
                        StringBuilder stringBuilder = new StringBuilder();
                        char[] buffer = new char[128]; int i;
                        while((i = inputStream.read(buffer)) != -1) stringBuilder.append(buffer, 0, i);
                        nPutClipboardData(stringBuilder.toString(), mimeType);
                    }
                    return;
                }else if(flavor.isRepresentationClassByteBuffer()) {
                    String charset = flavor.getParameter("charset");
                    if(charset == null) charset = "UTF-8";
                    ByteBuffer clipData = (ByteBuffer) transferable.getTransferData(flavor);
                    nPutClipboardData(new String(clipData.array(), charset), mimeType);
                    return;
                }else if(flavor.isRepresentationClassInputStream()) {
                    String charset = flavor.getParameter("charset");
                    if(charset == null) charset = "UTF-8";
                    try (InputStream inputStream = (InputStream) transferable.getTransferData(flavor); 
                            ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[128]; int i;
                        while((i = inputStream.read(buffer)) != -1) baos.write(buffer, 0, i);
                        nPutClipboardData(baos.toString(charset), mimeType);
                    }
                    return;
                }
            }catch(UnsupportedFlavorException | IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    
    private static native void nQuerySystemClipboard();
    private static native void nPutClipboardData(String clipboardData, String clipboardDataMime);
}
