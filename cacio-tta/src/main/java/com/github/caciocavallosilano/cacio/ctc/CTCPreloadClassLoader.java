/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.github.caciocavallosilano.cacio.ctc;

import javax.swing.plaf.metal.MetalLookAndFeel;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.*;
import java.util.Arrays;
import java.util.Objects;
import sun.misc.Unsafe;

public class CTCPreloadClassLoader extends URLClassLoader {
    static {
        try {
            Field toolkit = Toolkit.class.getDeclaredField("toolkit");
            toolkit.setAccessible(true);
            toolkit.set(null, new CTCToolkit());

            Field defaultHeadlessField = GraphicsEnvironment.class.getDeclaredField("defaultHeadless");
            defaultHeadlessField.setAccessible(true);
            defaultHeadlessField.set(null,Boolean.TRUE);
            Field headlessField = GraphicsEnvironment.class.getDeclaredField("headless");
            headlessField.setAccessible(true);
            headlessField.set(null,Boolean.TRUE);

            Class<?> geCls = Class.forName("java.awt.GraphicsEnvironment$LocalGE");
            Field ge = geCls.getDeclaredField("INSTANCE");
            ge.setAccessible(true);
            defaultHeadlessField.set(null, Boolean.FALSE);
            headlessField.set(null,Boolean.FALSE);

            Class<?> smfCls = Class.forName("sun.java2d.SurfaceManagerFactory");
            Field smf = smfCls.getDeclaredField("instance");
            smf.setAccessible(true);
            smf.set(null, null);

            setFinalStatic(ge, new CTCGraphicsEnvironment());

            String propertyFontManager = System.getProperty("cacio.font.fontmanager");
            if (propertyFontManager != null) {
                FontManagerUtil.setFontManager(propertyFontManager);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.setProperty("swing.defaultlaf", MetalLookAndFeel.class.getName());
    }

    public CTCPreloadClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
        try {
            System.setProperty("java.class.path", System.getProperty("java.class.path") + ":" + new File(url.toURI()).getAbsolutePath());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    static URL getFileURL(File file) {
        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {}

        try {
            return file.toURL();
        } catch (MalformedURLException e) {
            // Should never happen since we specify the protocol...
            throw new InternalError(e);
        }
    }

    private void appendToClassPathForInstrumentation(String path) {
        assert(Thread.holdsLock(this));
        super.addURL(getFileURL(new File(path)));
    }

    // https://stackoverflow.com/a/71465198
    public static void setFinalStatic(Field field, Object value) throws Exception{
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Object fieldBase = unsafe.staticFieldBase(field);
        long fieldOffset = unsafe.staticFieldOffset(field);

        unsafe.putObject(fieldBase, fieldOffset, value);
    }
}
