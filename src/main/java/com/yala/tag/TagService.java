package com.yala.tag;
import com.yala.model.*;

import com.yala.tag.dto.TagRequest;
import com.yala.tag.dto.TagResponse;
import java.util.List;

public interface TagService {

    List<TagResponse> findAll();

    TagResponse findById(Long id);

    TagResponse create(TagRequest request);

    TagResponse update(Long id, TagRequest request);

    void delete(Long id);
}
