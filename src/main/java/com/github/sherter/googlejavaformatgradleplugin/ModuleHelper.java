/*
 * Copyright 2016-2022 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.sherter.googlejavaformatgradleplugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import sun.misc.Unsafe;

final class ModuleHelper {
  private static final Logger log = Logging.getLogger(ModuleHelper.class);

  // prevent direct instantiation
  private ModuleHelper() {}

  private static final Map<String, String> REQUIRED_PACKAGES_TO_TEST_CLASSES = new HashMap<>();

  static {
    REQUIRED_PACKAGES_TO_TEST_CLASSES.putIfAbsent("com.sun.tools.javac.util", "Context");
    REQUIRED_PACKAGES_TO_TEST_CLASSES.putIfAbsent("com.sun.tools.javac.file", "CacheFSInfo");
    REQUIRED_PACKAGES_TO_TEST_CLASSES.putIfAbsent("com.sun.tools.javac.tree", "TreeTranslator");
    REQUIRED_PACKAGES_TO_TEST_CLASSES.putIfAbsent("com.sun.tools.javac.parser", "Tokens$TokenKind");
    REQUIRED_PACKAGES_TO_TEST_CLASSES.putIfAbsent(
        "com.sun.tools.javac.api", "DiagnosticFormatter$PositionKind");
  }

  private static boolean checkDone = false;

  public static synchronized void doOpenInternalPackagesIfRequired() {
    if (jvmVersion() < 16 || checkDone) {
      return;
    }
    try {
      checkDone = true;
      final List<String> unavailableRequiredPackages = unavailableRequiredPackages();
      if (!unavailableRequiredPackages.isEmpty()) {
        openPackages(unavailableRequiredPackages);
        final List<String> failedToOpen = unavailableRequiredPackages();
        if (!failedToOpen.isEmpty()) {
          final StringBuilder message = new StringBuilder();
          message.append(
              "WARNING: Some required internal classes are unavailable. Please consider adding the following JVM arguments\n");
          message.append("WARNING: ");
          for (String name : failedToOpen) {
            message.append(String.format("--add-opens jdk.compiler/%s=ALL-UNNAMED", name));
          }
          log.warn("{}", message);
        }
      }
    } catch (Throwable e) {
      log.error("WARNING: Failed to check for available JDK packages.", e);
    }
  }

  private static List<String> unavailableRequiredPackages() {
    final List<String> packages = new ArrayList<>();
    for (Map.Entry<String, String> e : REQUIRED_PACKAGES_TO_TEST_CLASSES.entrySet()) {
      final String key = e.getKey();
      final String value = e.getValue();
      try {
        final Class<?> clazz = Class.forName(key + "." + value);
        if (clazz.isEnum()) {
          clazz.getMethod("values").invoke(null);
        } else {
          clazz.getDeclaredConstructor().newInstance();
        }
      } catch (IllegalAccessException ex) {
        packages.add(key);
      } catch (Exception ignore) {
        // in old versions of JDK some classes could be unavailable
      }
    }
    return packages;
  }

  @SuppressWarnings("unchecked")
  private static void openPackages(Collection<String> packagesToOpen) throws Throwable {
    final Collection<?> modules = allModules();
    if (modules == null) {
      return;
    }
    final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
    unsafeField.setAccessible(true);
    final Unsafe unsafe = (Unsafe) unsafeField.get(null);
    final Field implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
    final MethodHandles.Lookup lookup =
        (MethodHandles.Lookup)
            unsafe.getObject(
                unsafe.staticFieldBase(implLookupField), unsafe.staticFieldOffset(implLookupField));
    final MethodHandle modifiers = lookup.findSetter(Method.class, "modifiers", Integer.TYPE);
    final Method exportMethod =
        Class.forName("java.lang.Module").getDeclaredMethod("implAddOpens", String.class);
    modifiers.invokeExact(exportMethod, Modifier.PUBLIC);
    for (Object module : modules) {
      final Collection<String> packages =
          (Collection<String>) module.getClass().getMethod("getPackages").invoke(module);
      for (String name : packages) {
        if (packagesToOpen.contains(name)) {
          exportMethod.invoke(module, name);
        }
      }
    }
  }

  @Nullable
  private static Collection<?> allModules() {
    // calling ModuleLayer.boot().modules() by reflection
    try {
      final Object boot = Class.forName("java.lang.ModuleLayer").getMethod("boot").invoke(null);
      if (boot == null) {
        return null;
      }
      final Object modules = boot.getClass().getMethod("modules").invoke(boot);
      return (Collection<?>) modules;
    } catch (Exception ignore) {
      return null;
    }
  }

  private static int jvmVersion() {
    String jre = System.getProperty("java.version");
    if (jre.startsWith("1.8")) {
      return 8;
    }
    Matcher matcher = Pattern.compile("(\\d+)").matcher(jre);
    if (!matcher.find()) {
      throw new IllegalArgumentException("Expected " + jre + " to start with an integer");
    }
    return Integer.parseInt(matcher.group(1));
  }
}
