package com.distelli.monitor.impl;

import com.distelli.monitor.Sequence;
import com.distelli.persistence.TableDescription;
import com.distelli.persistence.Index;
import com.distelli.persistence.AttrType;
import com.distelli.jackson.transform.TransformModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.inject.Inject;

public class SequenceImpl implements Sequence {
    public static TableDescription getTableDescription() {
        return TableDescription.builder()
            .tableName("monitor-sequences")
            .index((idx) -> idx
                   .hashKey("nam", AttrType.STR))
            .build();
    }

    private final Index<SequenceInfo> _sequences;
    private final ObjectMapper _om = new ObjectMapper();

    public static class SequenceInfo {
        public String name;
        public Long next;
    }

    private TransformModule createTransforms(TransformModule module) {
        module.createTransform(SequenceInfo.class)
            .put("nxt", Long.class, "next")
            .put("nam", String.class, "name");
        return module;
    }

    @Inject
    protected SequenceImpl(Index.Factory indexFactory) {
        _om.registerModule(createTransforms(new TransformModule()));

        _sequences = indexFactory.create(SequenceInfo.class)
            .withTableDescription(getTableDescription())
            .withNoEncrypt("nxt")
            .withConvertValue(_om::convertValue)
            .build();
    }

    @Override
    public long next(String name) {
        return _sequences.updateItem(name, null)
            .increment("nxt", 1)
            .returnAllNew()
            .always()
            .next;
    }
}
