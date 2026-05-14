package com.cafepos.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TagGroup {
    private final int id;
    private final String name;
    private final boolean multiSelect;
    private final List<Tag> tags = new ArrayList<>();

    public TagGroup(int id, String name, boolean multiSelect) {
        this.id = id;
        this.name = name;
        this.multiSelect = multiSelect;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isMultiSelect() {
        return multiSelect;
    }

    public void addTag(Tag tag) {
        if (tag != null) {
            tags.add(tag);
        }
    }

    public List<Tag> getTags() {
        return Collections.unmodifiableList(tags);
    }
}
