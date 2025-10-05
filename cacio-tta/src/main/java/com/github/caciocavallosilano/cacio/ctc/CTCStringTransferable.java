/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.github.caciocavallosilano.cacio.ctc;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author maks
 */
public class CTCStringTransferable implements Transferable {
    static {
        ArrayList<DataFlavor> dataFlavorsList = new ArrayList<>();
        try {
            generateSupportedFlavorTypes(dataFlavorsList);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        STRING_DATA_FLAVORS = dataFlavorsList.toArray(new DataFlavor[0]);
        try {
           generateSupportedFlavorsForMimeType(dataFlavorsList, "text/html");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        HTML_DATA_FLAVORS = dataFlavorsList.toArray(new DataFlavor[0]);
    }
    private static final DataFlavor[] STRING_DATA_FLAVORS;
    private static final DataFlavor[] HTML_DATA_FLAVORS;
    private final String data;
    private final boolean isHtml;
    
    public CTCStringTransferable(String data, String textType) {
        this.data = data;
        isHtml = textType.endsWith("html");
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return isHtml ? HTML_DATA_FLAVORS : STRING_DATA_FLAVORS;
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor.isFlavorTextType();
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if(!flavor.isFlavorTextType()) throw new UnsupportedFlavorException(flavor);
        if(CharSequence.class.isAssignableFrom(flavor.getRepresentationClass())) {
            return data;
        }else if(flavor.isRepresentationClassByteBuffer()) {
            return ByteBuffer.wrap(data.getBytes(StandardCharsets.UTF_8));
        }else if(flavor.isRepresentationClassCharBuffer()) {
            return CharBuffer.wrap(data);
        }else if(flavor.isRepresentationClassInputStream()) {
            return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        }else if(flavor.isRepresentationClassReader()) {
            return new StringReader(data);
        }
        throw new UnsupportedFlavorException(flavor);
    }
    
    public static void generateSupportedFlavorsChar(List<DataFlavor> outputFlavors, String mimeType) throws ClassNotFoundException {
        outputFlavors.add(new DataFlavor(mimeType+"; class=java.io.Reader; charset=Unicode"));
        outputFlavors.add(new DataFlavor(mimeType+"; class=java.lang.String; charset=Unicode"));
        outputFlavors.add(new DataFlavor(mimeType+"; class=java.nio.CharBuffer; charset=Unicode"));
        outputFlavors.add(new DataFlavor(mimeType+"; class=\"[C\"; charset=Unicode"));
    }
    
    public static void generateSupportedFlavorsBinary(List<DataFlavor> outputFlavors, String mimeType, String encoding, boolean isDefault) throws ClassNotFoundException {
        outputFlavors.add(new DataFlavor(mimeType+"; class=java.io.InputStream; charset="+encoding));
        if(isDefault)
            outputFlavors.add(new DataFlavor(mimeType+"; class=java.io.InputStream"));
        outputFlavors.add(new DataFlavor(mimeType+"; class=java.nio.ByteBuffer; charset="+encoding));
        outputFlavors.add(new DataFlavor(mimeType+"; class=\"[B\"; charset="+encoding));
    }
    
    public static void generateSupportedFlavorsForMimeType(List<DataFlavor> outputFlavors, String mimeType) throws ClassNotFoundException {
        generateSupportedFlavorsChar(outputFlavors, mimeType);
        String defaultCharsetName = Charset.defaultCharset().name();
        for(String charset : Charset.availableCharsets().keySet()) {
            generateSupportedFlavorsBinary(outputFlavors, mimeType, charset, defaultCharsetName.equals(charset));
        }
    }
    
    public static void generateSupportedFlavorTypes(List<DataFlavor> outputFlavors) throws ClassNotFoundException {
        outputFlavors.add(DataFlavor.stringFlavor);
        generateSupportedFlavorsForMimeType(outputFlavors, "text/plain");
        generateSupportedFlavorsForMimeType(outputFlavors, "text/x-java");
        outputFlavors.add(new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType+"; class=java.lang.String"));
    }
}
