/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common;

import com.blaxk.spawnelytra.common.config.MapConfigView;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Test helper: loads YAML (classpath fixture or project file) into a {@link MapConfigView}. */
public final class TestYaml {
    private TestYaml() {
    }

    public static MapConfigView resource(final String name) {
        try (InputStream in = TestYaml.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalArgumentException("missing fixture " + name);
            }
            return TestYaml.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Loads a file relative to the project root (Gradle runs tests with the project dir as working dir). */
    public static MapConfigView file(final String relative) {
        try {
            return TestYaml.parse(Files.readString(TestYaml.project().resolve(relative), StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path project() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("common").resolve("CORE-API.md"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("project root not found");
        }
        return p;
    }

    public static MapConfigView parse(final String yaml) {
        String text = yaml;
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        final Object o = new Yaml().load(text);
        return MapConfigView.fromMap(o instanceof final Map<?, ?> m ? m : Map.of());
    }

    public static String dump(final MapConfigView view) {
        return new Yaml().dump(view.toMap());
    }
}
