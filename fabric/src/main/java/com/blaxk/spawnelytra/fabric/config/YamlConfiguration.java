/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.config;

import org.slf4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.AnchorNode;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.reader.UnicodeReader;
import org.yaml.snakeyaml.representer.Representer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Comment-preserving YAML configuration that follows Bukkit's {@code YamlConfiguration}
 * load/save algorithm (same SnakeYAML options, same header/comment handling), so files
 * written by the Fabric port are interchangeable with the ones written by the Paper plugin.
 */
public class YamlConfiguration extends ConfigSection {
    private static final class Constructor extends SafeConstructor {
        Constructor(final LoaderOptions options) {
            super(options);
        }

        Object construct(final Node node) {
            return this.constructObject(node);
        }

        void flatten(final MappingNode node) {
            this.flattenMapping(node);
        }
    }

    private final DumperOptions dumperOptions;
    private final LoaderOptions loaderOptions;
    private final Constructor constructor;
    private final Representer representer;
    private final Yaml yaml;

    private List<String> header = new ArrayList<>();
    private List<String> footer = new ArrayList<>();
    private YamlConfiguration defaults;

    public YamlConfiguration() {
        this.dumperOptions = new DumperOptions();
        this.dumperOptions.setIndent(2);
        this.dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        this.dumperOptions.setProcessComments(true);
        this.dumperOptions.setWidth(80);

        this.loaderOptions = new LoaderOptions();
        this.loaderOptions.setMaxAliasesForCollections(Integer.MAX_VALUE);
        this.loaderOptions.setCodePointLimit(Integer.MAX_VALUE);
        this.loaderOptions.setNestingDepthLimit(100);
        this.loaderOptions.setProcessComments(true);

        this.constructor = new Constructor(this.loaderOptions);
        this.representer = new Representer(this.dumperOptions);
        this.representer.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        this.yaml = new Yaml(this.constructor, this.representer, this.dumperOptions, this.loaderOptions);
    }

    public YamlConfiguration getDefaults() {
        return this.defaults;
    }

    public void setDefaults(final YamlConfiguration defaults) {
        this.defaults = defaults;
    }

    // ---- loading ---------------------------------------------------------------------------

    /** Loads a file like Bukkit's {@code YamlConfiguration.loadConfiguration(File)}. */
    public static YamlConfiguration loadConfiguration(final File file, final Logger logger) {
        final YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (final Exception e) {
            if (logger != null) {
                logger.error("Cannot load {}", file, e);
            }
        }
        return config;
    }

    public static YamlConfiguration loadConfiguration(final InputStream in) {
        final YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (final Exception ignored) {
        }
        return config;
    }

    public void load(final File file) throws IOException, InvalidConfigurationException {
        this.loadFromString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    public void loadFromString(final String contents) throws InvalidConfigurationException {
        final MappingNode node;
        try (Reader reader = new UnicodeReader(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)))) {
            final Node raw = this.yaml.compose(reader);
            if (raw != null && !(raw instanceof MappingNode)) {
                throw new InvalidConfigurationException("Top level is not a Map.");
            }
            node = (MappingNode) raw;
        } catch (final InvalidConfigurationException e) {
            throw e;
        } catch (final Exception e) {
            throw new InvalidConfigurationException(e.getMessage(), e);
        }

        this.map.clear();
        if (node != null) {
            adjustNodeComments(node);
            this.header = loadHeader(getCommentLines(node.getBlockComments()));
            this.footer = getCommentLines(node.getEndComments());
            this.fromNodeTree(node, this);
        }
    }

    private static void adjustNodeComments(final MappingNode node) {
        if (node.getBlockComments() == null && !node.getValue().isEmpty()) {
            final Node keyNode = node.getValue().getFirst().getKeyNode();
            final List<CommentLine> lines = keyNode.getBlockComments();
            if (lines != null && !lines.isEmpty()) {
                int index = -1;
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).getCommentType() == CommentType.BLANK_LINE) {
                        index = i;
                    }
                }
                if (index != -1) {
                    node.setBlockComments(new ArrayList<>(lines.subList(0, index + 1)));
                    keyNode.setBlockComments(new ArrayList<>(lines.subList(index + 1, lines.size())));
                }
            }
        }
    }

    private void fromNodeTree(final MappingNode input, final ConfigSection section) {
        this.constructor.flatten(input);
        for (final NodeTuple tuple : input.getValue()) {
            final Node key = tuple.getKeyNode();
            final String keyString = String.valueOf(this.constructor.construct(key));
            Node value = tuple.getValueNode();
            while (value instanceof final AnchorNode anchor) {
                value = anchor.getRealNode();
            }
            if (value instanceof final MappingNode mapping) {
                this.fromNodeTree(mapping, section.createSection(keyString));
            } else {
                section.set(keyString, this.constructor.construct(value));
            }
            section.setComments(keyString, getCommentLines(key.getBlockComments()));
            if (value instanceof MappingNode || value instanceof SequenceNode) {
                section.setInlineComments(keyString, getCommentLines(key.getInLineComments()));
            } else {
                section.setInlineComments(keyString, getCommentLines(value.getInLineComments()));
            }
        }
    }

    private static List<String> loadHeader(final List<String> header) {
        final LinkedList<String> list = new LinkedList<>(header);
        if (!list.isEmpty()) {
            list.removeLast();
        }
        while (!list.isEmpty() && list.peek() == null) {
            list.remove();
        }
        return list;
    }

    private static List<String> saveHeader(final List<String> header) {
        final LinkedList<String> list = new LinkedList<>(header);
        if (!list.isEmpty()) {
            list.add(null);
        }
        return list;
    }

    private static List<String> getCommentLines(final List<CommentLine> comments) {
        final List<String> lines = new ArrayList<>();
        if (comments != null) {
            for (final CommentLine comment : comments) {
                if (comment.getCommentType() == CommentType.BLANK_LINE) {
                    lines.add(null);
                } else {
                    final String line = comment.getValue();
                    lines.add(line.startsWith(" ") ? line.substring(1) : line);
                }
            }
        }
        return lines;
    }

    private static List<CommentLine> getCommentLines(final List<String> comments, final CommentType type) {
        final List<CommentLine> lines = new ArrayList<>();
        for (final String comment : comments) {
            if (comment == null) {
                lines.add(new CommentLine(null, null, "", CommentType.BLANK_LINE));
            } else {
                lines.add(new CommentLine(null, null, comment.isEmpty() ? comment : " " + comment, type));
            }
        }
        return lines;
    }

    // ---- saving ----------------------------------------------------------------------------

    public String saveToString() {
        final MappingNode node = this.toNodeTree(this);
        node.setBlockComments(getCommentLines(saveHeader(this.header), CommentType.BLOCK));
        node.setEndComments(getCommentLines(this.footer, CommentType.BLOCK));

        final StringWriter writer = new StringWriter();
        if (node.getBlockComments().isEmpty() && node.getEndComments().isEmpty() && node.getValue().isEmpty()) {
            writer.write("");
        } else {
            if (node.getValue().isEmpty()) {
                node.setFlowStyle(DumperOptions.FlowStyle.FLOW);
            }
            this.yaml.serialize(node, writer);
        }
        return writer.toString();
    }

    public void save(final File file) throws IOException {
        final File parent = file.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        Files.writeString(file.toPath(), this.saveToString(), StandardCharsets.UTF_8);
    }

    private MappingNode toNodeTree(final ConfigSection section) {
        final List<NodeTuple> tuples = new ArrayList<>();
        for (final Map.Entry<String, Entry> e : section.map.entrySet()) {
            final Node key = this.representer.represent(e.getKey());
            final Object raw = e.getValue().value;
            final Node value = raw instanceof final ConfigSection sub ? this.toNodeTree(sub) : this.representer.represent(raw);
            key.setBlockComments(getCommentLines(e.getValue().comments, CommentType.BLOCK));
            if (value instanceof MappingNode || value instanceof SequenceNode) {
                key.setInLineComments(getCommentLines(e.getValue().inlineComments, CommentType.IN_LINE));
            } else {
                value.setInLineComments(getCommentLines(e.getValue().inlineComments, CommentType.IN_LINE));
            }
            tuples.add(new NodeTuple(key, value));
        }
        return new MappingNode(Tag.MAP, tuples, DumperOptions.FlowStyle.BLOCK);
    }

    public static final class InvalidConfigurationException extends Exception {
        public InvalidConfigurationException(final String message) {
            super(message);
        }

        public InvalidConfigurationException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
