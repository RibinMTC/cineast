package org.vitrivr.cineast.core.db.dao.reader;

import org.vitrivr.cineast.core.data.providers.primitive.PrimitiveTypeProvider;
import org.vitrivr.cineast.core.data.tag.CompleteTag;
import org.vitrivr.cineast.core.data.tag.Tag;
import org.vitrivr.cineast.core.db.DBSelector;
import org.vitrivr.cineast.core.db.PersistencyWriter;
import org.vitrivr.cineast.core.db.RelationalOperator;

import java.io.Closeable;
import java.util.*;
import java.util.stream.Collectors;

public class TagReader implements Closeable {


    private final DBSelector selector;

    /**
     * Name of the entity that contains the {@link Tag}s.
     */
    public static final String TAG_ENTITY_NAME = "cineast_tags";

    public static final String TAG_ID_COLUMNNAME= "id";
    public static final String TAG_NAME_COLUMNNAME = "name";
    public static final String TAG_DESCRIPTION_COLUMNNAME = "description";

    /**
     * A map containing cached {@link Tag}s.
     */
    private final HashMap<String, Tag> tagCache = new HashMap<>();

    /**
     * Constructor for {@link TagReader}
     *
     * @param selector {@link DBSelector} instanced used for lookup of tags.
     */
    public TagReader(DBSelector selector) {
        this.selector = selector;

        if (this.selector == null) {
            throw new NullPointerException("selector cannot be null");
        }

        this.selector.open(TAG_ENTITY_NAME);
    }

    /**
     * Returns all {@link Tag}s that match the specified name. For matching, case-insensitive a left and right side truncation comparison is used.
     * The matching tags are returned in order of their expected relevance.
     *
     * @param name To value with which to match the {@link Tag}s.
     * @return List of matching {@link Tag}s.
     */
    public List<Tag> getTagsByMatchingName(final String name) {
        final String lname = name.toLowerCase();
//        return this.selector.getRows(TAG_NAME_COLUMNNAME, RelationalOperator.ILIKE, lname).stream()
//                .map(TagReader::fromMap)
        List<Tag> tags = tagCache.values().stream().filter(x -> x.getName().toLowerCase().contains(lname))
                .sorted((o1, o2) -> {
                    boolean o1l = o1.getName().toLowerCase().startsWith(lname);
                    boolean o2l = o2.getName().toLowerCase().startsWith(lname);
                    boolean o1e = o1.getName().toLowerCase().equals(lname);
                    boolean o2e = o2.getName().toLowerCase().equals(lname);
                    if (o1e && !o2e) {
                        return -1;
                    } else if (!o1e && o2e) {
                        return 1;
                    } else if (o1l && !o2l) {
                        return -1;
                    } else if (!o1l && o2l) {
                        return 1;
                    } else {
                        return o1.getName().compareTo(o2.getName());
                    }
                })
                .collect(Collectors.toList());
        return tags;

    }

    /**
     * Returns all {@link Tag}s that are equal to the specified name.
     *
     * @param name To value with which to compare the {@link Tag}s.
     * @return List of matching {@link Tag}s.
     */
    public List<Tag> getTagsByName(String name) {
        List<Map<String, PrimitiveTypeProvider>> rows = this.selector.getRows("name", name);
        ArrayList<Tag> _return = new ArrayList<>(rows.size());
        for (Map<String, PrimitiveTypeProvider> row : rows) {
            Tag t = fromMap(row);
            if (t != null) {
                _return.add(t);
            }
        }
        return _return;
    }

    public Tag getTagById(String id) {
        if (id == null) {
            return null;
        }
        List<Map<String, PrimitiveTypeProvider>> rows = this.selector.getRows("id", id);
        if (rows.isEmpty()) {
            return null;
        }
        return fromMap(rows.get(0));

    }

    public List<Tag> getTagsById(String... ids) {
        if (ids == null) {
            return null;
        }
        List<Map<String, PrimitiveTypeProvider>> rows = this.selector.getRows("id", ids);
        if (rows.isEmpty()) {
            return null;
        }
        ArrayList<Tag> _return = new ArrayList<>(rows.size());
        for (Map<String, PrimitiveTypeProvider> row : rows) {
            Tag t = fromMap(row);
            if (t != null) {
                _return.add(t);
            }
        }
        return _return;

    }

    /**
     * Returns a list of all {@link Tag}s contained in the database.
     * <p>
     *
     * @return List of all {@link Tag}s contained in the database
     */
    public List<Tag> getAll() {
        return this.selector.getAll().stream().map(TagReader::fromMap).filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Returns a list of all cached {@link Tag}s.
     *
     * @return List of all {@link Tag}s contained in the cache.
     */
    public List<Tag> getAllCached() {
        return new ArrayList<>(this.tagCache.values());
    }

    public void initCache() {
        List<Tag> all = getAll();
        for (Tag tag : all) {
            this.tagCache.put(tag.getId(), tag);
        }
    }

    public void flushCache() {
        this.tagCache.clear();
    }

    public Tag getCachedById(String id) {
        return this.tagCache.get(id);
    }

    public List<Tag> getCachedByName(String name) {
        ArrayList<Tag> _return = new ArrayList<>();
        for (Tag t : this.tagCache.values()) {
            if (t.getName().equals(name)) {
                _return.add(t);
            }
        }
        return _return;
    }

    private static Tag fromMap(Map<String, PrimitiveTypeProvider> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        if (!map.containsKey("id") || !map.containsKey("name")) {
            return null;
        }

        if (!map.containsKey("description")) {
            return new CompleteTag(map.get("id").getString(), map.get("name").getString(), "");
        } else {
            return new CompleteTag(map.get("id").getString(), map.get("name").getString(),
                    map.get("description").getString());
        }

    }

    @Override
    public void close() {
        this.selector.close();
    }


}