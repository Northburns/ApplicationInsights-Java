/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.microsoft.applicationinsights.internal.perfcounter;

import com.microsoft.applicationinsights.internal.logger.InternalLogger;
import com.microsoft.applicationinsights.internal.system.SystemInformation;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * This class serves as the connection to the native code that does the work with Windows.
 *
 * Created by gupele on 3/30/2015.
 */
public final class JniPCConnector {
    public static final String PROCESS_SELF_INSTANCE_NAME = "__SELF__";

    private static final String BITS_MODEL_64 = "64";
    private static final String NATIVE_LIBRARY_64 = "applicationinsights-core-native-win64";

    private static String currentInstanceName;

    // Native methods are private and can be accessed through matching public methods in the class.
    private native static String getInstanceName(int processId);

    private native static String addCounter(String category, String counter, String instance);

    private native static double getPerformanceCounterValue(String name);

    /**
     * This method must be called before any other method.
     * All other methods are relevant only if this method was successful.
     *
     * Note that the method should not throw and should gracefully return the boolean value.
     * @return True on success.
     */
    public static boolean initialize() {
        try {
            if (!SystemInformation.INSTANCE.isWindows()) {
                InternalLogger.INSTANCE.error("Jni connector is only used on Windows OS.");
                return false;
            }

            if (loadLibrary()) {
                return initNativeCode();
            }
        } catch (Throwable e) {
            InternalLogger.INSTANCE.error("Failed to load native dll, Windows performance counters will not be used: '%s'.", e.getMessage());
        }

        return false;
    }

    /**
     * Adding a performance counter
     * @param category The category must be non null non empty value.
     * @param counter The counter must be non null non empty value.
     * @param instance The instance.
     * @return True on success.
     */
    public static String addPerformanceCounter(String category, String counter, String instance) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(category), "category must be non-null non empty string.");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(counter), "counter must be non-null non empty string.");

        return addCounter(category, counter, instance);
    }

    /**
     * Process instance name is only known at runtime, therefore process level performance counters
     * should use the 'PROCESS_SELF_INSTANCE_NAME' as the requested process name and then call this
     * method to translate that logical name into the actual name that is fetched from the native code.
     * @param instanceName The raw instance name
     * @return The actual instance name.
     * @throws Exception If instanceName equals PROCESS_SELF_INSTANCE_NAME but the actual instance name is unknown.
     */
    public static String translateInstanceName(String instanceName) throws Exception {
        if (PROCESS_SELF_INSTANCE_NAME.equals(instanceName)) {
            if (Strings.isNullOrEmpty(currentInstanceName)) {
                throw new Exception("Cannot translate instance name: Unknown current instance name");
            }

            return currentInstanceName;
        }

        return instanceName;
    }

    /**
     * This method will delegate the call to the native code after the proper sanity checks.
     * @param name The logical name of the performance counter as was fetched during the 'addPerformanceCounter' call.
     * @return The current value.
     */
    public static double getValueOfPerformanceCounter(String name) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "name must be non-null non empty value.");

        return getPerformanceCounterValue(name);
    }

    private static boolean initNativeCode() {
        int processId = Integer.parseInt(SystemInformation.INSTANCE.getProcessId());

        currentInstanceName = getInstanceName(processId);
        if (Strings.isNullOrEmpty(currentInstanceName)) {
            InternalLogger.INSTANCE.error("Failed to fetch current process instance name, process counters for for the process level will not be activated.");
        }
        return true;
    }

    private static boolean loadLibrary() {
        String model = System.getProperty("sun.arch.data.model");
        String libraryToLoad = NATIVE_LIBRARY_64;
        if (!BITS_MODEL_64.equals(model)) {
            return false;
        }

        System.loadLibrary(libraryToLoad);
        InternalLogger.INSTANCE.trace("Successfully loaded library '%s'", libraryToLoad);
        return true;
    }
}
