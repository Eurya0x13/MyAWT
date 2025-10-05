package com.github.caciocavallosilano.cacio.ctc;

import java.awt.AWTError;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;

import sun.font.*;

class FontManagerUtil {

    // A way to force set a FontManager
    static void setFontManager(final String fmClassName) {
        AccessController.doPrivileged(new PrivilegedAction<Object>() {

                public Object run() {
                    try {
                        System.setProperty("sun.font.fontmanager", fmClassName);

                        Class<?> fmClass = Class.forName(fmClassName);
                        FontManager instance = (FontManager) fmClass.getDeclaredConstructor().newInstance();

                        Field fmInstanceField = FontManagerFactory.class.getDeclaredField("instance");
                        fmInstanceField.setAccessible(true);
                        fmInstanceField.set(null, instance);

                        String currName = FontManagerFactory.getInstance().getClass().getName();
                        if (!currName.equals(fmClassName)) {
                            System.err.println("Could not change font manager to " + fmClassName + ", current was " + currName);
                        }
                    } catch (ReflectiveOperationException ex) {
                        ex.printStackTrace();
                        throw new InternalError(ex);
                    }
                    return null;
                }
            });
    }

    static void setFontScaler(final String fsClassName) {
        AccessController.doPrivileged(new PrivilegedAction<Object>() {

                public Object run() {
                    try {
                        // FontScaler instance = (FontManager) fmClass.getDeclaredConstructor().newInstance();

                        Field fmInstanceField = FontScaler.class.getDeclaredField("scalerConstructor");
                        fmInstanceField.setAccessible(true);
                        fmInstanceField.set(null, Class.forName(fsClassName).getConstructor(Font2D.class, int.class, boolean.class, int.class));
                    } catch (Throwable ex) {
                        ex.printStackTrace();
                        throw new InternalError(ex);
                    }
                    return null;
                }
            });
    }
}
